import * as Y from 'yjs'
import { jwtVerify } from 'jose'
import { prosemirrorJSONToYXmlFragment, yXmlFragmentToProsemirrorJSON } from '@tiptap/y-tiptap'
import { schema, htmlToDoc, jsonToHtml } from './schema.js'
import { mergeNote } from './merge.js'

export const FIELD = 'default' // Tiptap Collaboration 확장의 기본 필드
const DOC_NAME = /^post:(\d+):(\d+)$/
// 워크스페이스 변경 알림 채널 (내용 없음, 알림만 주고받는다)
const WORKSPACE_NAME = /^workspace:(\d+)$/

export function parseDocumentName(name) {
  const match = DOC_NAME.exec(name || '')
  if (match) return { workspaceId: Number(match[1]), postId: Number(match[2]) }
  const ws = WORKSPACE_NAME.exec(name || '')
  if (ws) return { workspaceId: Number(ws[1]), postId: null, channel: true }
  return null
}

const MAX_NOTIFY_BODY = 4096

function readBody(request) {
  return new Promise((resolve, reject) => {
    let size = 0
    const chunks = []
    request.on('data', (chunk) => {
      size += chunk.length
      if (size > MAX_NOTIFY_BODY) {
        reject(new Error('too large'))
        request.destroy()
        return
      }
      chunks.push(chunk)
    })
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
    request.on('error', reject)
  })
}

/** Y 텍스트를 바뀐 부분만 고친다 (다른 사람의 커서·입력이 흔들리지 않게) */
function setTextMinimal(yText, next) {
  const current = yText.toString()
  if (current === next) return
  let start = 0
  while (start < current.length && start < next.length && current[start] === next[start]) start++
  let endCur = current.length
  let endNext = next.length
  while (endCur > start && endNext > start && current[endCur - 1] === next[endNext - 1]) {
    endCur--
    endNext--
  }
  if (endCur > start) yText.delete(start, endCur - start)
  if (endNext > start) yText.insert(start, next.slice(start, endNext))
}

/**
 * HTML과 제목을 Y 문서에 반영한다. 통째로 지우고 다시 쓰지 않고 바뀐 부분만 고친다
 * (편집 중인 사람의 커서와 그 순간의 입력을 지키기 위해)
 */
export function fillFromHtml(ydoc, title, html, origin) {
  const fragment = ydoc.getXmlFragment(FIELD)
  const yTitle = ydoc.getText('title')
  ydoc.transact(() => {
    prosemirrorJSONToYXmlFragment(schema, htmlToDoc(html).toJSON(), fragment)
    setTextMinimal(yTitle, title || '')
  }, origin)
}

/** Y 문서 → 저장할 제목·HTML */
export function readDocument(ydoc) {
  const json = yXmlFragmentToProsemirrorJSON(ydoc.getXmlFragment(FIELD))
  return {
    title: ydoc.getText('title').toString(),
    content: jsonToHtml(json.type ? json : { type: 'doc', content: [] }),
  }
}

export function createCollabHandlers({ apiUrl, secret, jwtSecret, fetchImpl = fetch, log = console }) {
  const key = new TextEncoder().encode(jwtSecret)
  // 문서별로 마지막으로 DB와 맞춘 해시 (다른 경로로 바뀐 걸 감지하는 데 쓴다)
  const baseHashes = new Map()
  // 문서별로 마지막으로 DB와 맞춘 제목·본문 (다른 경로의 수정과 합칠 때 기준)
  const baseContents = new Map()
  // DB 저장이 실패한 문서 (다시 성공하면 알려 준다)
  const storeFailed = new Set()

  async function call(path, init = {}) {
    const res = await fetchImpl(`${apiUrl}/internal/collab${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', 'X-Collab-Secret': secret, ...(init.headers || {}) },
    })
    const body = res.headers.get('content-type')?.includes('json') ? await res.json() : null
    return { status: res.status, body }
  }

  /**
   * 다른 경로(목록에서 이름 변경, 예전 앱의 저장 등)로 DB가 바뀐 내용을 실시간 편집 문서에 합친다.
   * 마지막으로 맞춘 내용(base) 대비 양쪽 수정을 블록 단위로 합치고, 같은 곳을 고쳤으면 실시간 편집 쪽을 남긴다.
   */
  function absorbExternal(documentName, document, latest) {
    if (latest.hash && latest.hash === baseHashes.get(documentName)) return false
    const mine = readDocument(document)
    const base = baseContents.get(documentName) ?? mine
    const merged = mergeNote(base, mine, { title: latest.title, content: latest.content })
    baseHashes.set(documentName, latest.hash)
    baseContents.set(documentName, { title: latest.title, content: latest.content })
    if (merged.title === mine.title && merged.content === mine.content) return false
    log.warn(`[collab] ${documentName}: 다른 경로의 수정을 합칩니다`)
    fillFromHtml(document, merged.title, merged.content, 'external')
    return true
  }

  /**
   * Y 문서를 DB에 저장한다. 그사이 다른 경로로 바뀌었으면(409) 합쳐서 한 번 더 저장한다.
   */
  async function storeDocument(documentName, document, attempt = 0) {
    const { workspaceId, postId } = parseDocumentName(documentName)
    const { title, content } = readDocument(document)
    const { status, body } = await call(`/workspaces/${workspaceId}/posts/${postId}`, {
      method: 'PUT',
      body: JSON.stringify({
        title,
        content,
        ydoc: Buffer.from(Y.encodeStateAsUpdate(document)).toString('base64'),
        baseHash: baseHashes.get(documentName) ?? null,
      }),
    })

    if (status === 200) {
      baseHashes.set(documentName, body.hash)
      baseContents.set(documentName, { title: body.title, content: body.content })
      if (storeFailed.delete(documentName)) notifyClients(document, { type: 'store-ok' })
    } else if (status === 409 && body) {
      absorbExternal(documentName, document, body)
      // 합친 결과를 바로 저장 (합친 결과가 실시간 편집 내용과 같아 문서 변경이 없을 때도 DB를 맞춘다)
      if (attempt < 1) await storeDocument(documentName, document, attempt + 1)
    } else if (status === 404) {
      log.warn(`[collab] ${documentName}: 노트가 없어 저장하지 않습니다 (삭제됨)`)
    } else {
      // 편집 중인 사람들에게 저장이 안 되고 있음을 알린다 (화면에 "저장됨"으로 계속 보이지 않게)
      storeFailed.add(documentName)
      notifyClients(document, { type: 'store-error', reason: status === 400 ? 'too-large' : 'failed' })
      throw new Error(`[collab] ${documentName}: 저장 실패 (${status})`)
    }
  }

  function notifyClients(document, message) {
    document.broadcastStateless?.(JSON.stringify(message))
  }

  /** 알림을 받으면 열려 있는 노트 문서에 DB의 새 내용을 바로 반영한다 */
  async function refreshFromDb(instance, workspaceId, postId) {
    const documentName = `post:${workspaceId}:${postId}`
    const document = instance.documents.get(documentName)
    if (!document || document.isLoading) return
    const { status, body } = await call(`/workspaces/${workspaceId}/posts/${postId}`)
    if (status !== 200 || !body) return
    absorbExternal(documentName, document, body)
    // 합친 결과가 DB와 다르면(같은 곳을 실시간 편집에서도 고친 경우) 바로 저장해 맞춘다
    const now = readDocument(document)
    if (now.title !== body.title || now.content !== body.content) await storeDocument(documentName, document)
  }

  return {
    async onAuthenticate({ token, documentName, connectionConfig }) {
      const target = parseDocumentName(documentName)
      if (!target) throw new Error('잘못된 문서 이름')

      let userId
      try {
        const { payload } = await jwtVerify(token || '', key, { algorithms: ['HS512', 'HS384', 'HS256'] })
        if (payload.type !== 'access') throw new Error('access 토큰이 아님')
        userId = Number(payload.sub)
      } catch {
        throw new Error('인증 실패')
      }

      const query = `userId=${userId}&workspaceId=${target.workspaceId}` + (target.channel ? '' : `&postId=${target.postId}`)
      const { status, body } = await call(`/access?${query}`)
      if (status !== 200 || !body?.canRead) throw new Error('권한 없음')
      // 알림 채널은 받기만 한다
      if (!body.canWrite || target.channel) connectionConfig.readOnly = true
      return { userId, workspaceId: target.workspaceId }
    },

    async onLoadDocument({ documentName, document }) {
      const { workspaceId, postId, channel } = parseDocumentName(documentName)
      if (channel) return document
      const { status, body } = await call(`/workspaces/${workspaceId}/posts/${postId}`)
      if (status !== 200) throw new Error(`노트를 불러오지 못함 (${status})`)

      if (body.ydoc) {
        // 저장된 편집 상태를 이어서 쓴다 (새로 만들면 예전 상태를 들고 다시 연결한 사람의 본문이 중복된다)
        Y.applyUpdate(document, Buffer.from(body.ydoc, 'base64'))
        // 그 뒤 다른 경로로 바뀌었으면 바뀐 부분만 반영
        if (body.stale) fillFromHtml(document, body.title, body.content, 'load')
      } else {
        // 처음 실시간 편집으로 여는 노트: HTML에서 만든다
        fillFromHtml(document, body.title, body.content, 'load')
      }
      baseHashes.set(documentName, body.hash)
      baseContents.set(documentName, body.stale || !body.ydoc ? { title: body.title, content: body.content } : readDocument(document))
      return document
    },

    async onStoreDocument({ documentName, document }) {
      const { channel } = parseDocumentName(documentName)
      if (channel) return
      await storeDocument(documentName, document)
    },

    /**
     * API → 편집 서버: 워크스페이스 변경 알림 (POST /internal/notify, 공유 비밀값 필요).
     * 그 워크스페이스 채널을 보고 있는 사람들에게 그대로 전달하고,
     * 멤버에서 빠졌거나 권한이 바뀐 사람(disconnectUserId)의 노트 편집 연결은 끊어 다시 권한을 확인하게 한다.
     */
    async onRequest({ request, response, instance }) {
      const path = (request.url || '').split('?')[0]
      if (path !== '/internal/notify') return // 기본 응답
      const reply = (code) => {
        response.writeHead(code, { 'Content-Type': 'text/plain' })
        response.end(String(code))
      }
      if (request.method !== 'POST' || request.headers['x-collab-secret'] !== secret) {
        reply(404)
        throw null // 기본 응답을 막는다
      }
      let payload
      try {
        payload = JSON.parse(await readBody(request))
      } catch {
        reply(400)
        throw null
      }
      const workspaceId = Number(payload?.workspaceId)
      if (!Number.isInteger(workspaceId) || workspaceId <= 0) {
        reply(400)
        throw null
      }
      const message = JSON.stringify({
        type: 'workspace-change',
        action: String(payload.action || 'change').slice(0, 40),
        actorId: payload.actorId ?? null,
        postId: payload.postId ?? null,
        folderId: payload.folderId ?? null,
        targetUserId: payload.targetUserId ?? null,
      })
      instance.documents.get(`workspace:${workspaceId}`)?.broadcastStateless(message)

      // 사람이 일반 저장으로 노트를 바꿨으면(목록에서 이름 변경 등), 그 노트를 실시간 편집 중인 사람들에게도 바로 반영
      // (실시간 편집 저장이 보낸 알림은 actorId가 없으므로 건너뛴다)
      const postId = Number(payload.postId)
      if (payload.action === 'updatePost' && payload.actorId != null && Number.isInteger(postId) && postId > 0) {
        refreshFromDb(instance, workspaceId, postId).catch((e) => log.warn(`[collab] 노트 갱신 실패: ${e.message}`))
      }

      const kickUserId = Number(payload.disconnectUserId)
      if (Number.isInteger(kickUserId) && kickUserId > 0) {
        const prefix = `post:${workspaceId}:`
        instance.documents.forEach((document, name) => {
          if (!name.startsWith(prefix)) return
          document.getConnections()
            .filter((connection) => Number(connection.context?.userId) === kickUserId)
            .forEach((connection) => connection.close())
        })
      }
      // 멤버에서 빠진 사람은 알림 채널도 끊는다 (알림을 받을 시간을 조금 준 뒤). 권한만 바뀐 경우엔 다시 연결된다
      if (Number.isInteger(kickUserId) && kickUserId > 0) {
        setTimeout(() => {
          instance.documents.get(`workspace:${workspaceId}`)?.getConnections()
            .filter((connection) => Number(connection.context?.userId) === kickUserId)
            .forEach((connection) => connection.close())
        }, 1500).unref?.()
      }
      reply(204)
      throw null
    },

    async afterUnloadDocument({ documentName }) {
      baseHashes.delete(documentName)
      baseContents.delete(documentName)
      storeFailed.delete(documentName)
    },
  }
}

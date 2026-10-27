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

/** HTML과 제목으로 Y 문서 내용을 채운다 (기존 내용은 지운다) */
export function fillFromHtml(ydoc, title, html, origin) {
  const fragment = ydoc.getXmlFragment(FIELD)
  const yTitle = ydoc.getText('title')
  ydoc.transact(() => {
    if (fragment.length > 0) fragment.delete(0, fragment.length)
    prosemirrorJSONToYXmlFragment(schema, htmlToDoc(html).toJSON(), fragment)
    if (yTitle.length > 0) yTitle.delete(0, yTitle.length)
    yTitle.insert(0, title || '')
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

  async function call(path, init = {}) {
    const res = await fetchImpl(`${apiUrl}/internal/collab${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', 'X-Collab-Secret': secret, ...(init.headers || {}) },
    })
    const body = res.headers.get('content-type')?.includes('json') ? await res.json() : null
    return { status: res.status, body }
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
        Y.applyUpdate(document, Buffer.from(body.ydoc, 'base64'))
      } else {
        // 아직 실시간 편집 상태가 없거나, 다른 경로로 바뀐 노트: HTML에서 새로 만든다
        fillFromHtml(document, body.title, body.content, 'load')
      }
      baseHashes.set(documentName, body.hash)
      baseContents.set(documentName, readDocument(document))
      return document
    },

    async onStoreDocument({ documentName, document }) {
      const { workspaceId, postId, channel } = parseDocumentName(documentName)
      if (channel) return
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
      } else if (status === 409 && body) {
        // 예전 방식(일반 저장)으로 그사이 바뀜: 그쪽 수정과 실시간 편집 내용을 블록 단위로 합쳐 문서에 반영하고,
        // 새 DB 내용을 기준으로 다시 저장되게 한다 (반영으로 문서가 바뀌므로 곧 다시 저장됨)
        log.warn(`[collab] ${documentName}: 다른 경로의 수정을 합칩니다`)
        const base = baseContents.get(documentName) ?? { title, content }
        const merged = mergeNote(base, { title, content }, { title: body.title, content: body.content })
        baseHashes.set(documentName, body.hash)
        baseContents.set(documentName, { title: body.title, content: body.content })
        fillFromHtml(document, merged.title, merged.content, 'external')
      } else if (status === 404) {
        log.warn(`[collab] ${documentName}: 노트가 없어 저장하지 않습니다 (삭제됨)`)
      } else {
        throw new Error(`[collab] ${documentName}: 저장 실패 (${status})`)
      }
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
      reply(204)
      throw null
    },

    async afterUnloadDocument({ documentName }) {
      baseHashes.delete(documentName)
      baseContents.delete(documentName)
    },
  }
}

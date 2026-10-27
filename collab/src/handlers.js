import * as Y from 'yjs'
import { jwtVerify } from 'jose'
import { prosemirrorJSONToYXmlFragment, yXmlFragmentToProsemirrorJSON } from '@tiptap/y-tiptap'
import { schema, htmlToDoc, jsonToHtml } from './schema.js'

export const FIELD = 'default' // Tiptap Collaboration 확장의 기본 필드
const DOC_NAME = /^post:(\d+):(\d+)$/

export function parseDocumentName(name) {
  const match = DOC_NAME.exec(name || '')
  if (!match) return null
  return { workspaceId: Number(match[1]), postId: Number(match[2]) }
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

      const { status, body } = await call(
        `/access?userId=${userId}&workspaceId=${target.workspaceId}&postId=${target.postId}`)
      if (status !== 200 || !body?.canRead) throw new Error('권한 없음')
      if (!body.canWrite) connectionConfig.readOnly = true
      return { userId }
    },

    async onLoadDocument({ documentName, document }) {
      const { workspaceId, postId } = parseDocumentName(documentName)
      const { status, body } = await call(`/workspaces/${workspaceId}/posts/${postId}`)
      if (status !== 200) throw new Error(`노트를 불러오지 못함 (${status})`)

      if (body.ydoc) {
        Y.applyUpdate(document, Buffer.from(body.ydoc, 'base64'))
      } else {
        // 아직 실시간 편집 상태가 없거나, 다른 경로로 바뀐 노트: HTML에서 새로 만든다
        fillFromHtml(document, body.title, body.content, 'load')
      }
      baseHashes.set(documentName, body.hash)
      return document
    },

    async onStoreDocument({ documentName, document }) {
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
      } else if (status === 409 && body) {
        // 예전 방식(일반 저장)으로 그사이 바뀜: DB 내용을 문서에 반영하고 그 기준으로 다시 저장되게 한다
        log.warn(`[collab] ${documentName}: 다른 경로의 수정을 반영합니다`)
        baseHashes.set(documentName, body.hash)
        fillFromHtml(document, body.title, body.content, 'external')
      } else if (status === 404) {
        log.warn(`[collab] ${documentName}: 노트가 없어 저장하지 않습니다 (삭제됨)`)
      } else {
        throw new Error(`[collab] ${documentName}: 저장 실패 (${status})`)
      }
    },

    async afterUnloadDocument({ documentName }) {
      baseHashes.delete(documentName)
    },
  }
}

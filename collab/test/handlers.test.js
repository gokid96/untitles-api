import { test } from 'node:test'
import assert from 'node:assert/strict'
import * as Y from 'yjs'
import { createCollabHandlers, fillFromHtml, readDocument, parseDocumentName } from '../src/handlers.js'

const HTML = '<h2>제목</h2><p>본문 <strong>굵게</strong> <a class="note-link" data-note-id="7">다른 노트</a></p>'
  + '<ul data-type="taskList"><li data-type="taskItem" data-checked="true"><p>할 일</p></li></ul>'
  + '<table><tbody><tr><th><p>a</p></th></tr><tr><td><p>b</p></td></tr></tbody></table>'

test('HTML ↔ Y 문서 변환에서 노트 링크·체크리스트·표가 유지된다', () => {
  const doc = new Y.Doc()
  fillFromHtml(doc, '노트', HTML, 'test')
  const { title, content } = readDocument(doc)
  assert.equal(title, '노트')
  assert.match(content, /data-note-id="7"/)
  assert.match(content, /data-type="taskItem"/)
  assert.match(content, /data-checked="true"/)
  assert.match(content, /<th[^>]*><p>a<\/p><\/th>/)
  assert.match(content, /<strong>굵게<\/strong>/)
})

test('문서 이름 형식', () => {
  assert.deepEqual(parseDocumentName('post:3:15'), { workspaceId: 3, postId: 15 })
  assert.equal(parseDocumentName('post:3'), null)
  assert.equal(parseDocumentName('../etc'), null)
})

function fakeApi(responses) {
  const calls = []
  const fetchImpl = async (url, init = {}) => {
    calls.push({ url, init })
    const r = responses.shift()
    return { status: r.status, headers: { get: () => 'application/json' }, json: async () => r.body }
  }
  return { calls, fetchImpl }
}

test('저장 충돌(409)이면 DB 내용을 문서에 반영하고 그 해시를 기준으로 삼는다', async () => {
  const api = fakeApi([
    { status: 200, body: { title: 't', content: '<p>처음</p>', hash: 'h1', ydoc: null } },
    { status: 409, body: { title: '바뀐 제목', content: '<p>예전 앱이 고침</p>', hash: 'h2' } },
    { status: 200, body: { hash: 'h3', title: '바뀐 제목', content: '<p>예전 앱이 고침</p>' } },
    { status: 200, body: { hash: 'h4', title: '바뀐 제목', content: '<p>예전 앱이 고침</p>' } },
  ])
  const h = createCollabHandlers({ apiUrl: 'http://api', secret: 's', jwtSecret: 'x'.repeat(64), fetchImpl: api.fetchImpl, log: { warn() {} } })
  const doc = new Y.Doc()
  await h.onLoadDocument({ documentName: 'post:1:2', document: doc })
  assert.match(readDocument(doc).content, /처음/)

  await h.onStoreDocument({ documentName: 'post:1:2', document: doc })
  assert.equal(JSON.parse(api.calls[1].init.body).baseHash, 'h1')
  assert.match(readDocument(doc).content, /예전 앱이 고침/)
  assert.equal(readDocument(doc).title, '바뀐 제목')

  // 합친 뒤 바로 다시 저장 (새 기준 h2)
  assert.equal(JSON.parse(api.calls[2].init.body).baseHash, 'h2')
  await h.onStoreDocument({ documentName: 'post:1:2', document: doc })
  assert.equal(JSON.parse(api.calls[3].init.body).baseHash, 'h3')
  assert.equal(api.calls[3].init.headers['X-Collab-Secret'], 's')
})

test('저장된 Y 상태가 있으면 HTML 대신 그 상태를 불러온다', async () => {
  const source = new Y.Doc()
  fillFromHtml(source, '저장본', '<p>Y 상태</p>', 'test')
  const ydoc = Buffer.from(Y.encodeStateAsUpdate(source)).toString('base64')
  const api = fakeApi([{ status: 200, body: { title: 'x', content: '<p>HTML</p>', hash: 'h', ydoc } }])
  const h = createCollabHandlers({ apiUrl: 'http://api', secret: 's', jwtSecret: 'x'.repeat(64), fetchImpl: api.fetchImpl })
  const doc = new Y.Doc()
  await h.onLoadDocument({ documentName: 'post:1:2', document: doc })
  assert.match(readDocument(doc).content, /Y 상태/)
  assert.equal(readDocument(doc).title, '저장본')
})

test('토큰이 없거나 틀리면 접속을 거부한다', async () => {
  const h = createCollabHandlers({ apiUrl: 'http://api', secret: 's', jwtSecret: 'x'.repeat(64), fetchImpl: async () => { throw new Error('호출되면 안 됨') } })
  await assert.rejects(h.onAuthenticate({ token: 'bad', documentName: 'post:1:2', connectionConfig: {} }))
})

test('다른 경로의 수정과 실시간 편집 내용을 블록 단위로 합친다 (어느 쪽 입력도 잃지 않음)', async () => {
  const api = fakeApi([
    { status: 200, body: { title: '회의록', content: '<p>하나</p><p>둘</p><p>셋</p>', hash: 'h1', ydoc: null } },
    { status: 409, body: { title: '회의록', content: '<p>하나</p><p>둘</p><p>셋 (예전 앱)</p>', hash: 'h2' } },
    { status: 200, body: { hash: 'h3', title: '회의록 v2', content: '<p>하나 (실시간)</p><p>둘</p><p>셋 (예전 앱)</p>' } },
  ])
  const h = createCollabHandlers({ apiUrl: 'http://api', secret: 's', jwtSecret: 'x'.repeat(64), fetchImpl: api.fetchImpl, log: { warn() {} } })
  const doc = new Y.Doc()
  await h.onLoadDocument({ documentName: 'post:1:2', document: doc })
  // 실시간 편집으로 첫 블록을 고침
  fillFromHtml(doc, '회의록 v2', '<p>하나 (실시간)</p><p>둘</p><p>셋</p>', 'test')

  await h.onStoreDocument({ documentName: 'post:1:2', document: doc })
  const { title, content } = readDocument(doc)
  assert.equal(title, '회의록 v2')
  assert.equal(content, '<p>하나 (실시간)</p><p>둘</p><p>셋 (예전 앱)</p>')
})

test('같은 블록을 양쪽이 고치면 실시간 편집 쪽을 남긴다', async () => {
  const { mergeNote } = await import('../src/merge.js')
  const merged = mergeNote(
    { title: 't', content: '<p>a</p><p>b</p>' },
    { title: 't', content: '<p>a 실시간</p><p>b</p>' },
    { title: 't2', content: '<p>a 예전앱</p><p>b</p><p>c</p>' },
  )
  assert.equal(merged.title, 't2')
  assert.equal(merged.content, '<p>a 실시간</p><p>b</p><p>c</p>')
})

test('통째로 다시 쓰지 않고 바뀐 부분만 고친다 (다른 사람의 커서 위치가 유지됨)', () => {
  const doc = new Y.Doc()
  fillFromHtml(doc, '제목', '<p>하나</p><p>둘</p>', 'test')
  const first = doc.getXmlFragment('default').get(0)
  const titleText = doc.getText('title')
  const pos = Y.createRelativePositionFromTypeIndex(titleText, 1)
  fillFromHtml(doc, '제목 바뀜', '<p>하나</p><p>둘 바뀜</p>', 'test')
  assert.equal(doc.getXmlFragment('default').get(0), first) // 첫 문단은 그대로 같은 노드
  assert.equal(Y.createAbsolutePositionFromRelativePosition(pos, doc).index, 1)
  assert.equal(readDocument(doc).content, '<p>하나</p><p>둘 바뀜</p>')
  assert.equal(readDocument(doc).title, '제목 바뀜')
})

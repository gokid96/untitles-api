// 실시간 편집 중인 노트가 다른 경로(예전 앱의 일반 저장 등)로도 바뀐 경우, 두 쪽 수정을 블록 단위로 합친다.
// 웹 편집기의 자동 병합(untitles-web/src/utils/noteMerge.js)과 같은 방식:
// 마지막으로 맞춘 내용(base), 실시간 편집 내용(mine), DB의 새 내용(theirs)을 문단·제목·목록·표 단위로 3-way 병합하고,
// 같은 블록을 양쪽이 다르게 고쳤으면 그 블록은 실시간 편집 쪽을 남긴다 (지금 화면에서 보고 있는 내용).
import { Window } from 'happy-dom'
import { diffIndices } from 'node-diff3'

const document = new Window().document

function parseFragment(html) {
  const template = document.createElement('template')
  template.innerHTML = html || ''
  return template.content
}

function childBlocks(parent) {
  return Array.from(parent.childNodes)
    .map((node) => (node.nodeType === 1 ? node.outerHTML : node.textContent))
    .filter((block) => block && block.trim() !== '')
}

export function splitBlocks(html) {
  return html ? childBlocks(parseFragment(html)) : []
}

const CONTAINER_TAGS = new Set(['UL', 'OL', 'LI', 'BLOCKQUOTE', 'TABLE', 'THEAD', 'TBODY', 'TR'])
const shellOf = (el) => el.cloneNode(false).outerHTML

function mergeElement(baseHtml, mineHtml, theirsHtml) {
  const [b, m, t] = [baseHtml, mineHtml, theirsHtml].map((html) => parseFragment(html).firstElementChild)
  if (!b || !m || !t || !CONTAINER_TAGS.has(b.tagName)) return null
  if (shellOf(b) !== shellOf(m) || shellOf(b) !== shellOf(t)) return null
  const merged = mergeBlocks(childBlocks(b), childBlocks(m), childBlocks(t))
  if (!merged) return null
  const out = b.cloneNode(false)
  out.innerHTML = merged.join('')
  return out.outerHTML
}

function hunksOf(base, other) {
  return diffIndices(base, other).map((h) => ({
    start: h.buffer1[0],
    end: h.buffer1[0] + h.buffer1[1],
    content: h.buffer2Content,
  }))
}

const sameHunk = (x, y) =>
  x.start === y.start && x.end === y.end && x.content.join('\u0000') === y.content.join('\u0000')

function overlap(x, y) {
  const xInsert = x.start === x.end
  const yInsert = y.start === y.end
  if (xInsert && yInsert) return x.start === y.start ? 'insert' : false
  if (xInsert) return x.start > y.start && x.start < y.end
  if (yInsert) return y.start > x.start && y.start < x.end
  return x.start < y.end && y.start < x.end
}

/**
 * @param {boolean} preferMine 같은 블록 충돌 시 mine을 남길지 (false면 null 반환)
 */
export function mergeBlocks(base, mine, theirs, preferMine = false) {
  const mineHunks = hunksOf(base, mine)
  const theirHunks = hunksOf(base, theirs)
  const kept = []
  for (const t of theirHunks) {
    let skip = false
    for (const m of mineHunks) {
      if (sameHunk(m, t)) { skip = true; break }
      const o = overlap(m, t)
      if (o === 'insert') {
        m.content = [...t.content, ...m.content.filter((block) => !t.content.includes(block))]
        skip = true
        break
      }
      if (o) {
        const single = m.start === t.start && m.end === t.end && m.end - m.start === 1 &&
          m.content.length === 1 && t.content.length === 1
        const inner = single ? mergeElement(base[m.start], m.content[0], t.content[0]) : null
        if (inner) {
          m.content = [inner]
        } else if (!preferMine) {
          return null
        }
        skip = true
        break
      }
    }
    if (!skip) kept.push(t)
  }
  const all = [...mineHunks, ...kept].sort((x, y) => x.start - y.start || (x.end - x.start) - (y.end - y.start))
  const result = []
  let index = 0
  for (const h of all) {
    result.push(...base.slice(index, h.start), ...h.content)
    index = h.end
  }
  result.push(...base.slice(index))
  return result
}

/** 제목·본문을 합친다. 같은 부분을 양쪽이 고쳤으면 실시간 편집 쪽(mine)을 남긴다 */
export function mergeNote(base, mine, theirs) {
  const title = mine.title === base.title ? theirs.title : mine.title
  let content
  if (mine.content === base.content) content = theirs.content
  else if (theirs.content === base.content || theirs.content === mine.content) content = mine.content
  else content = mergeBlocks(splitBlocks(base.content), splitBlocks(mine.content), splitBlocks(theirs.content), true).join('')
  return { title, content }
}

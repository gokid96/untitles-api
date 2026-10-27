// 웹 편집기(untitles-web PostEditor)와 같은 문서 구조. 노드·마크 이름과 속성이 같아야 한다.
// 화면 관련 기능(자동완성, 클릭 등)은 서버에 필요 없어 구조만 둔다.
import { Window } from 'happy-dom'
import { getSchema, Node, mergeAttributes } from '@tiptap/core'
import { DOMParser, DOMSerializer } from '@tiptap/pm/model'
import StarterKit from '@tiptap/starter-kit'
import TaskList from '@tiptap/extension-task-list'
import TaskItem from '@tiptap/extension-task-item'
import Highlight from '@tiptap/extension-highlight'
import TextAlign from '@tiptap/extension-text-align'
import { Table, TableRow, TableCell, TableHeader } from '@tiptap/extension-table'
import Image from '@tiptap/extension-image'

const window = new Window()
const document = window.document

// 노트 링크: <a class="note-link" data-note-id="12">제목</a>
const NoteLink = Node.create({
  name: 'noteLink',
  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,
  addAttributes() {
    return {
      postId: {
        default: null,
        parseHTML: (el) => {
          const id = Number(el.getAttribute('data-note-id'))
          return Number.isFinite(id) && id > 0 ? id : null
        },
        renderHTML: (attrs) => ({ 'data-note-id': attrs.postId }),
      },
      label: {
        default: '',
        parseHTML: (el) => el.textContent || '',
        renderHTML: () => ({}),
      },
    }
  },
  parseHTML() {
    return [{ tag: 'a[data-note-id]', priority: 60 }]
  },
  renderHTML({ node, HTMLAttributes }) {
    return ['a', mergeAttributes({ class: 'note-link' }, HTMLAttributes), node.attrs.label || '무제']
  },
})

export const extensions = [
  StarterKit.configure({ link: { openOnClick: false, HTMLAttributes: { target: '_blank', rel: 'noopener noreferrer' } } }),
  TaskList,
  TaskItem.configure({ nested: true }),
  Highlight,
  TextAlign.configure({ types: ['heading', 'paragraph'] }),
  Table.configure({ resizable: true }),
  TableRow,
  TableCell,
  TableHeader,
  Image.configure({ inline: false, allowBase64: false }),
  NoteLink,
]

export const schema = getSchema(extensions)

/** HTML → ProseMirror 문서 */
export function htmlToDoc(html) {
  const container = document.createElement('div')
  container.innerHTML = html || ''
  return DOMParser.fromSchema(schema).parse(container)
}

/** ProseMirror 문서(JSON) → HTML */
export function jsonToHtml(json) {
  const node = schema.nodeFromJSON(json)
  const fragment = DOMSerializer.fromSchema(schema).serializeFragment(node.content, { document })
  const container = document.createElement('div')
  container.appendChild(fragment)
  return container.innerHTML
}

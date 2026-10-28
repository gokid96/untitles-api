// Untitles 실시간 노트 편집 서버 (Hocuspocus)
// - 접속: wss://<API 호스트>/collab, 문서 이름은 "post:<워크스페이스ID>:<노트ID>", 토큰은 API의 액세스 토큰
// - 권한·저장은 API의 내부 엔드포인트(/internal/collab)를 통해 처리한다
import * as Sentry from '@sentry/node'
import { Server } from '@hocuspocus/server'

// 오류 추적 (SENTRY_DSN이 있을 때만). 노트 내용은 보내지 않는다
if (process.env.SENTRY_DSN) {
  Sentry.init({
    dsn: process.env.SENTRY_DSN,
    environment: process.env.SENTRY_ENVIRONMENT || 'production',
    release: process.env.SENTRY_RELEASE || undefined,
    sendDefaultPii: false,
    tracesSampleRate: 0,
  })
}
import { createCollabHandlers } from './handlers.js'

const required = (name) => {
  const value = process.env[name]
  if (!value) {
    console.error(`[collab] 환경변수 ${name} 가 없습니다`)
    process.exit(1)
  }
  return value
}

const handlers = createCollabHandlers({
  apiUrl: process.env.API_INTERNAL_URL || 'http://api:8070',
  secret: required('COLLAB_SECRET'),
  jwtSecret: required('JWT_SECRET'),
  onError: (error) => Sentry.captureException(error),
})

const server = new Server({
  name: 'untitles-collab',
  port: Number(process.env.PORT || 1234),
  // 입력이 멈추고 2초 뒤 저장, 계속 입력해도 최소 10초마다 저장
  debounce: 2000,
  maxDebounce: 10000,
  quiet: true,
  // 메시지 하나의 최대 크기 (기본 100MB는 너무 커서 메모리를 쉽게 잡아먹을 수 있다). 노트 저장 한도(약 0.5MB)보다 넉넉하게
  websocketOptions: { maxPayload: 8 * 1024 * 1024 },
  ...handlers,
})

server.listen().then(() => console.log(`[collab] listening on ${server.configuration.port}`))

const shutdown = async () => {
  // 종료 전에 열려 있는 문서를 저장한다
  await server.destroy()
  process.exit(0)
}
process.on('SIGTERM', shutdown)
process.on('SIGINT', shutdown)

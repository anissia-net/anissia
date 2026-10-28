# Anissia WEB (front-end)
- 애니시아 프론트엔드 WEB 입니다.
- 주소 : [anissia.net](https://anissia.net)
- 인프라 자원상 Admin 사이트를 포함합니다.
- 패키지 관리자로 npm을 사용합니다.

## 준비과정
IDE 혹은 텍스트 에디터를 설치하고 Node.js와 함께 제공되는 npm을 사용합니다.
1. IDE 내지 텍스트 에디터 설치
    * IDE는 **Visual Studio Code (무료)**, **WebStorm (유료)** 추천
    * **Visual Studio Code**
        * https://code.visualstudio.com
        * 확장도구(extensions)도 설치
            * [Vue VS Code Extension Pack](https://marketplace.visualstudio.com/items?itemName=sdras.vue-vscode-extensionpack)
        * git 으로 받은 후 열기
    * **WebStorm**
        * https://www.jetbrains.com/webstorm/
1. node.js를 [공식 홈페이지](https://nodejs.org/ko)에서 제공하는 인스톨러를 통해 설치하거나, NVM, NVM for Windows, NVS 등의 버전 관리자를 통해 설치하세요. 버전 관리자를 사용하는 방식이 추천(특히, 여러 프로젝트를 작업하는 경우)됩니다.
1. Node.js와 npm 설치 확인
   ``` shell
   node --version
   npm --version
   ```
## 의존성 설치
저장소 루트에서 `anissia-web` 폴더로 이동한 뒤 `package-lock.json`에 고정된 버전으로 설치합니다.
   ``` shell
   cd anissia-web
   npm ci
   ```

## 실행
1. 실행
    * IDE 에서 다음 명령어 실행
        * **운영서버** API 사용 - https://anissia.net
            ``` shell
            npm run dev -- --mode prod-server-api
            ```
        * **로컬서버** API 사용 - http://localhost:8001
            - 우선 코어서버(백엔드)를 가동해야 합니다.
            - 참고 : [anissia-core](https://github.com/anissia-net/anissia/tree/master/anissia-core)
            ``` shell
            npm run dev
            ```
2. 접속
    * 메인 : http://localhost:5173/
    * 어드민 : http://localhost:5173/admin/
        * 로그인-권한필요
        * 로컬의 경우 CORE 프로젝트에서 자동생성되는 **운영자** 계정으로 접근
    * 편성표 :
        * 2024년 버전 : http://localhost:5173/schedule/2024
        * 2015년 버전 : http://localhost:5173/schedule/2015
        * 2009년 버전 : http://localhost:5173/schedule/2009

## 빌드
빌드결과 폴더 : 프로젝트폴더/dist
``` shell
npm run build
```

- 운영 빌드는 편집기·관리자 화면까지 JS 1개와 CSS 1개로 합칩니다. 개발 서버에서는 기존 모듈 구조를 유지합니다.
- 빌드 시 생성하는 `dist/_headers`는 해당 빌드의 JS·CSS 파일에만 `public, max-age=31536000, immutable`을 적용합니다. 파일명 해시가 바뀌면 새 파일을 받으며, HTML과 없는 파일의 SPA 대체 응답은 매번 재검증합니다.
- 편성표·작품별 자막 데이터는 프런트에서 캐시하지 않습니다. 같은 요일을 다시 눌러도 API에 새로 요청하며, 공개 조회에는 DAT 인증 헤더를 보내지 않습니다. 관리자 요청의 인증은 유지합니다.
- 사이트의 편성표 미리보기는 Vue 컴포넌트로 직접 표시합니다. 외부 사이트에 삽입하는 iframe 주소와 색상 코드는 그대로 지원합니다.

## Cloudflare 로컬 검증
``` shell
npm run cf:preview
```

`wrangler.jsonc`의 `build.command`가 빌드한 뒤 로컬 Workers 정적 서버를 실행합니다. 소스 변경 시 다시 빌드하며, 이 명령은 운영 배포를 하지 않습니다. 정적 자산만 제공하므로 Node.js 런타임 호환 플래그는 사용하지 않습니다.

전송 압축은 Cloudflare가 클라이언트의 `Accept-Encoding`에 따라 처리합니다. 별도 `.gz`·`.br` 파일이나 고정 `Content-Encoding` 헤더를 배포하지 않습니다. Google Fonts·분석 스크립트 등 외부 서비스의 리소스는 앱 번들 통합 대상에 포함되지 않습니다.

## 참고
* [애니시아 문서](https://github.com/anissia-net/anissia/tree/master/docs)
* [애니편성표 API](https://github.com/anissia-net/anissia/tree/master/docs/anime_schdule.md)
* [애니시아 CORE 프로젝트](https://github.com/anissia-net/anissia/tree/master/anissia-core)
* [애니시아 WEB 프로젝트](https://github.com/anissia-net/anissia/tree/master/anissia-web)

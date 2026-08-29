# Anissia WEB (front-end)
- 애니시아 프론트엔드 WEB 입니다.
- 주소 : [anissia.net](https://anissia.net)
- 인프라 자원상 Admin 사이트를 포함합니다.
- 패키지 관리자로 yarn 4.x를 사용합니다.

## 준비과정
IDE 혹은 텍스트 에디터 설치하고 node.js와 yarn을 컴퓨터에 설치해야 합니다.
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
1. yarn 설치
   ```
   npm install --global yarn
   ```
## 의존성 설치
IDE 혹은 터미널(반드시 cd 등의 명령어를 통해 프로젝트 폴더로 이동해야함)에서 다음 명령어 실행. Visual Studio Code 사용시 콘솔 기본값은 PowerShell 이 아닌 cmd 추천
   ``` shell
   yarn install
   ```
## 프로젝트의 yarn 버전 업데이트 방법
프로젝트 자체의 yarn 버전 업데이트 시에 만 사용합니다.
   ``` shell
   yarn set version stable
   yarn install
   ```

## 정기적 package.json 업데이트
프로젝트 자체의 yarn 버전 업데이트 시에 만 사용합니다.
1. yarn-upgrade-all 설치
    ``` shell
    npm install -g yarn-upgrade-all
    ```
2. 실행
    ``` shell
    yarn-upgrade-all
    ```

## 실행
1. 실행
    * IDE 에서 다음 명령어 실행
        * **운영서버** API 사용 - https://anissia.net
            ``` shell
            yarn run dev --mode prod-server-api
            ```
        * **로컬서버** API 사용 - http://localhost:8001
            - 우선 코어서버(백엔드)를 가동해야 합니다.
            - 참고 : [anissia-core](https://github.com/anissia-net/core)
            ``` shell
            yarn run dev
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
yarn run build
```

## 참고
* [애니시아 문서](https://github.com/anissia-net/document)
* [애니편성표 API](https://github.com/anissia-net/document/blob/main/api_anime_schdule.md)
* [애니시아 CORE 프로젝트](https://github.com/anissia-net/core)
* [애니시아 WEB 프로젝트](https://github.com/anissia-net/web)

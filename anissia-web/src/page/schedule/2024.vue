<template>
  <div id="sc2024">
    <div class="notice">
      <div class="title">애니메이션 편성표</div>
      <p>애니편성표 2026 리뉴얼 버전이 나왔습니다.</p>
      <a href="https://anissia.net/schedule" target="_blank">새 편성표 보기 <i class="fa-solid fa-arrow-up-right-from-square"></i></a>
    </div>
  </div>
</template>

<style scoped>
#sc2024 {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: #fff;
  color: #27272a;
  font-family: "Noto Sans KR", -apple-system, BlinkMacSystemFont, "Malgun Gothic", sans-serif;
  text-align: center;
}
.title { font-size: 24px; font-weight: 200 }
p { margin: 16px 0 20px; font-size: 15px; color: #52525b }
a {
  display: inline-block;
  padding: 8px 16px;
  border: 1px solid #d4d4d8;
  border-radius: 3px;
  font-size: 14px;
  font-weight: 700;
  color: #2563eb;
  text-decoration: none;
  transition: border-color .2s, background-color .2s;
}
a:hover { border-color: #2563eb; background: #eff6ff }
a i { margin-left: 4px; font-size: 12px }

@media (prefers-color-scheme: dark) {
  #sc2024 { background: #000; color: #a1a1aa }
  p { color: #71717a }
  a { border-color: #27272a; color: #3b82f6 }
  a:hover { border-color: #3b82f6; background: #0b1220 }
}
</style>

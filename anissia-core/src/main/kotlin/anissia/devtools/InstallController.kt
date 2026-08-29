package anissia.devtools

import anissia.account.domain.Account
import anissia.account.domain.AccountRole
import anissia.account.repository.AccountRepository
import anissia.anime.domain.AnimeGenre
import anissia.anime.repository.AnimeGenreRepository
import anissia.board.domain.BoardTicker
import anissia.board.repository.BoardTickerRepository
import org.springframework.context.annotation.Profile
import anissia.security.PasswordHasher
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Profile("dev", "local")
@RestController
class InstallController(
    private val accountRepository: AccountRepository,
    private val animeGenreRepository: AnimeGenreRepository,
    private val boardTickerRepository: BoardTickerRepository,
    private val passwordHasher: PasswordHasher,
) {
    @Transactional
    @GetMapping("/install", produces = ["text/plain"])
    fun install(): String {
        val sb = StringBuilder()

        sb.append("설치를 시작합니다. (개발중)\n")

        if (accountRepository.count() == 0L) {
            accountRepository.save(
                Account(
                    email = "admin@anissia.net",
                    password = passwordHasher.hash("admin"),
                    name = "admin",
                    roles = mutableSetOf(AccountRole.ROOT),
                ),
            )
            (1..5).forEach {
                accountRepository.save(
                    Account(
                        email = "user$it@anissia.net",
                        password = passwordHasher.hash("user"),
                        name = "user$it",
                    ),
                )
            }
            sb.append("계정을 생성합니다.\n")
            sb.append("운영자: admin@anissia.net / admin")
            (1..5).forEach { sb.append("유저: user$it@anissia.net / user") }
        } else {
            sb.append("이미 계정이 생성되어 있습니다.\n")
        }

        if (animeGenreRepository.count() == 0L) {
            animeGenreRepository.saveAll(GENRES.map { AnimeGenre(it) })
            sb.append("장르를 생성 했습니다.\n")
        } else {
            sb.append("이미 장르가 생성되어 있습니다.\n")
        }

        if (boardTickerRepository.count() == 0L) {
            boardTickerRepository.save(
                BoardTicker("inquiry", "문의 게시판", "", "", "본 게시판은 애니시아 사이트에 대한 문의를 올리는 장소입니다."),
            )
            boardTickerRepository.save(BoardTicker("notice", "공지사항", "ROOT,TRANSLATOR", "", "내용"))
            sb.append("게시판을 생성 했습니다.\n")
        } else {
            sb.append("이미 게시판이 생성되어 있습니다.\n")
        }

        return sb.toString()
    }

    companion object {
        private val GENRES = listOf(
            "BL", "GL", "OTT", "SF", "TS", "개그", "게임", "고어", "공포", "금융", "기타", "내정", "드라마", "레이싱",
            "로맨스", "마법소녀", "메르헨", "메카닉", "모험", "무협", "미소녀", "미스터리", "밀리터리", "변신", "순정",
            "스릴러", "스포츠", "시대물", "아동", "아이돌", "액션", "연애", "요괴", "우주", "음악", "이세계", "일상",
            "일어더빙", "추리", "치유", "코미디", "코스프레", "판타지", "패러디", "퍼즐", "학원", "현대", "호러", "환경",
        )
    }
}

package anissia.wiki

import anissia.anime.domain.Anime
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface AnimeWikiRepository : JpaRepository<AnimeWiki, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Anime a where a.animeNo = :animeNo")
    fun lockAnime(animeNo: Long): Anime?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from AnimeWiki w where w.animeNo = :animeNo")
    fun lockWiki(animeNo: Long): AnimeWiki?
}

interface WikiTemplateRepository : JpaRepository<WikiTemplate, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WikiTemplate t where t.templateNo = :templateNo")
    fun lockTemplate(templateNo: Long): WikiTemplate?
    fun findAllByDeletedAndTitleContainingOrderByTitle(deleted: Boolean, q: String, pageable: Pageable): Page<WikiTemplate>
    fun existsByTitleAndTemplateNoNot(title: String, templateNo: Long): Boolean
}

interface WikiRevisionRepository : JpaRepository<WikiRevision, Long> {
    @Query(value = """
        select new anissia.wiki.WikiRecentItem(r.kind, r.targetNo, coalesce(a.subject, t.title), r.revision,
            r.summary, r.updatedBy, r.updatedAt, coalesce(t.deleted, false), a.thumbnail)
        from WikiRevision r
        left join Anime a on r.kind = 'anime' and a.animeNo = r.targetNo
        left join WikiTemplate t on r.kind = 'template' and t.templateNo = r.targetNo
        where (r.kind = 'anime' and a.animeNo is not null)
            or (r.kind = 'template' and t.templateNo is not null and (t.deleted = false or :includeDeleted = true))
        order by r.id desc
    """, countQuery = """
        select count(r) from WikiRevision r
        left join Anime a on r.kind = 'anime' and a.animeNo = r.targetNo
        left join WikiTemplate t on r.kind = 'template' and t.templateNo = r.targetNo
        where (r.kind = 'anime' and a.animeNo is not null)
            or (r.kind = 'template' and t.templateNo is not null and (t.deleted = false or :includeDeleted = true))
    """)
    fun recent(includeDeleted: Boolean, pageable: Pageable): Page<WikiRecentItem>
    fun findAllByKindAndTargetNoOrderByRevisionDesc(kind: String, targetNo: Long, pageable: Pageable): List<WikiRevision>
    fun findByKindAndTargetNoAndRevision(kind: String, targetNo: Long, revision: Long): WikiRevision?
    @Modifying
    @Query("delete from WikiRevision r where r.kind = :kind and r.targetNo = :targetNo")
    fun deleteAllByKindAndTargetNo(kind: String, targetNo: Long)
}

interface WikiReferenceRepository : JpaRepository<WikiReference, Long> {
    @Query(value = """
        select new anissia.wiki.WikiUsageItem('anime', a.animeNo, a.subject)
        from WikiReference r join Anime a on r.ownerKind = 'anime' and a.animeNo = r.ownerNo
        where r.targetKind = 'template' and r.targetId = :templateId order by a.subject
    """, countQuery = """
        select count(r) from WikiReference r join Anime a on r.ownerKind = 'anime' and a.animeNo = r.ownerNo
        where r.targetKind = 'template' and r.targetId = :templateId
    """)
    fun templateUsage(templateId: String, pageable: Pageable): Page<WikiUsageItem>
    @Modifying
    @Query("delete from WikiReference r where r.ownerKind = :kind and r.ownerNo = :ownerNo")
    fun deleteAllByOwnerKindAndOwnerNo(kind: String, ownerNo: Long)

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByTargetKindAndTargetId(kind: String, targetId: String): WikiReference?
}

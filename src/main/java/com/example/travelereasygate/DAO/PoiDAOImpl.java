package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.Poi;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class PoiDAOImpl implements PoiDAO {

    private final EntityManager em;

    @Autowired
    public PoiDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(Poi poi) {
        if (poi.getCreatedAt() == null) {
            poi.setCreatedAt(LocalDateTime.now());
        }
        if (poi.getPID() == 0) {
            em.persist(poi);
        } else {
            em.merge(poi);
        }
    }

    @Override
    public Poi findById(int PID) {
        return em.find(Poi.class, PID);
    }

    // 平台共用庫 (AID IS NULL, 但排除掉這間旅行社已經改寫/隱藏過的, 見 PoiOverride) + 該旅行社自建的 POI
    // (自建的 POI 裡也包含「改寫共用庫景點」產生出來的複本, 複本本身跟一般自建景點完全沒有差別)
    private static final String SHARED_OR_OWN_CLAUSE =
            "(p.AID = :aid OR (p.AID IS NULL AND p.PID NOT IN " +
            "(SELECT po.originalPid FROM PoiOverride po WHERE po.AID = :aid))) ";

    @Override
    public List<Poi> findByAgencyOrShared(Integer AID) {
        return em.createQuery(
                        "SELECT p FROM Poi p WHERE " + SHARED_OR_OWN_CLAUSE + "ORDER BY p.PID DESC", Poi.class)
                .setParameter("aid", AID)
                .getResultList();
    }

    // 自建行程時, 左側資料庫依「這個行程的國家 + 地區」自動篩選相關景點
    // 國家沒填就退回顯示全部; 地區沒填就只比對國家 (city 用模糊比對, 避免「花蓮」跟「花蓮縣」對不上)
    //
    // country / region 都可能是「日本、泰國」「熊本 福岡」這種用頓號/逗號/斜線/空格混著分隔的多個地名合併字串
    // (多國行程, 或使用者一次打好幾個地區時) —— 拆成多個 token 後用「符合任一個」比對, 不能整包拿去做
    // exact match / LIKE (那樣幾乎不可能跟資料庫裡任何單一地名的值對上, 等於篩不到任何資料)
    @Override
    public List<Poi> findByAgencyAndCountry(Integer AID, String country, String region) {
        List<String> countries = splitLocationTokens(country);
        if (countries.isEmpty()) {
            return findByAgencyOrShared(AID);
        }
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM Poi p WHERE " + SHARED_OR_OWN_CLAUSE + "AND p.country IN :countries ");
        // 地區條件不分國家數量, 只要有填就套用: city LIKE 是跟實際地名字串比對, 不同國家的地名本來就不會
        // 剛好撞名到互相誤篩 (例如「東京」不會比對到台灣的 POI), 所以多國行程時也可以正常套用地區篩選,
        // 不需要像先前那樣限制只有剛好選一個國家才套用 (那個限制反而讓多國行程沒辦法縮小範圍到指定城市)。
        List<String> regions = splitLocationTokens(region);
        boolean hasRegion = !regions.isEmpty();
        if (hasRegion) {
            StringBuilder regionClause = new StringBuilder("AND (p.city IS NULL");
            for (int i = 0; i < regions.size(); i++) {
                regionClause.append(" OR p.city LIKE :region").append(i);
            }
            regionClause.append(") ");
            jpql.append(regionClause);
        }
        jpql.append("ORDER BY p.PID DESC");

        var query = em.createQuery(jpql.toString(), Poi.class)
                .setParameter("aid", AID)
                .setParameter("countries", countries);
        if (hasRegion) {
            for (int i = 0; i < regions.size(); i++) {
                query.setParameter("region" + i, "%" + regions.get(i) + "%");
            }
        }
        return query.getResultList();
    }

    // 把「日本、泰國」「熊本 福岡」「印度/不丹」這種用頓號、逗號(全形/半形)、斜線、直線或空格混著分隔的
    // 合併字串拆成乾淨的 token 清單 (去除空白/重複/空字串); 國家、地區欄位共用同一套拆解規則
    private List<String> splitLocationTokens(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split("[、,，/|\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<Poi> searchByKeyword(Integer AID, String keyword, String category) {
        return searchByKeyword(AID, keyword, category, null);
    }

    @Override
    public List<Poi> searchByKeyword(Integer AID, String keyword, String category, String location) {
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM Poi p WHERE " + SHARED_OR_OWN_CLAUSE);
        if (keyword != null && !keyword.isBlank()) {
            jpql.append("AND (p.name LIKE :kw OR p.originalName LIKE :kw OR p.city LIKE :kw OR p.country LIKE :kw) ");
        }
        if (category != null && !category.isBlank()) {
            jpql.append("AND p.category = :category ");
        }
        if (location != null && !location.isBlank()) {
            jpql.append("AND (p.country LIKE :loc OR p.city LIKE :loc) ");
        }
        jpql.append("ORDER BY p.PID DESC");

        TypedQuery<Poi> query = em.createQuery(jpql.toString(), Poi.class);
        query.setParameter("aid", AID);
        if (keyword != null && !keyword.isBlank()) {
            query.setParameter("kw", "%" + keyword + "%");
        }
        if (category != null && !category.isBlank()) {
            query.setParameter("category", category);
        }
        if (location != null && !location.isBlank()) {
            query.setParameter("loc", "%" + location + "%");
        }
        return query.getResultList();
    }

    // 「建立新行程」頁面「目的地國家」欄位自動完成用。原本這個欄位是純自由文字, 跟後端 findByAgencyAndCountry()
    // 一樣拿去比對, 但完全不保證使用者打的地名資料庫裡真的有——這正是使用者建立「義大利/羅馬、威尼斯、比薩、
    // 米蘭」行程時, AI 完全找不到符合景點、只能建立空白行程的根本原因: 打的地名資料庫裡根本沒有這筆資料可以比對。
    // 改成只列出「公司景點資料庫裡實際存在」的國家 (共用庫 + 自己的), 使用者選出來的國家保證查得到東西
    // (至少國家層級一定有資料, 不會再發生選了資料庫裡根本沒有的地名這種落空的狀況)。
    @Override
    public List<String> findDistinctCountries(Integer AID) {
        return em.createQuery(
                        "SELECT DISTINCT p.country FROM Poi p WHERE " + SHARED_OR_OWN_CLAUSE +
                                "AND p.country IS NOT NULL AND p.country <> '' ORDER BY p.country ASC", String.class)
                .setParameter("aid", AID)
                .getResultList();
    }

    // 「地區/城市」欄位自動完成用, 依已選定的「目的地國家」篩選出該國實際存在的城市清單 (呼應「地區依國家判斷」
    // 的需求; 這裡用 country 精確比對, 因為傳進來的 country 一定是從 findDistinctCountries() 選出來的值,
    // 不像 findByAgencyAndCountry() 那樣要處理使用者自由輸入的合併字串, 不需要再拆 token)
    @Override
    public List<String> findDistinctCitiesByCountry(Integer AID, String country) {
        if (country == null || country.isBlank()) return List.of();
        return em.createQuery(
                        "SELECT DISTINCT p.city FROM Poi p WHERE " + SHARED_OR_OWN_CLAUSE +
                                "AND p.country = :country AND p.city IS NOT NULL AND p.city <> '' ORDER BY p.city ASC", String.class)
                .setParameter("aid", AID)
                .setParameter("country", country)
                .getResultList();
    }

    @Override
    @Transactional
    public void deleteById(int PID) {
        Poi poi = em.find(Poi.class, PID);
        if (poi != null) {
            em.remove(poi);
        }
    }
}
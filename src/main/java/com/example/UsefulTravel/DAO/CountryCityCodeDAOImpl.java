package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.CountryCityCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class CountryCityCodeDAOImpl implements CountryCityCodeDAO {

    private final EntityManager em;

    @Autowired
    public CountryCityCodeDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(CountryCityCode countryCityCode) {
        if (countryCityCode.getCreatedAt() == null) {
            countryCityCode.setCreatedAt(LocalDateTime.now());
        }
        if (countryCityCode.getCCID() == 0) {
            em.persist(countryCityCode);
        } else {
            em.merge(countryCityCode);
        }
    }

    @Override
    public CountryCityCode findById(int CCID) {
        return em.find(CountryCityCode.class, CCID);
    }

    @Override
    public List<CountryCityCode> searchByKeyword(String keyword, String type) {
        StringBuilder jpql = new StringBuilder("SELECT c FROM CountryCityCode c WHERE 1=1 ");
        if (keyword != null && !keyword.isBlank()) {
            jpql.append("AND (c.name LIKE :kw OR c.code LIKE :kw) ");
        }
        if (type != null && !type.isBlank()) {
            jpql.append("AND c.type = :type ");
        }
        jpql.append("ORDER BY c.type ASC, c.name ASC");

        TypedQuery<CountryCityCode> query = em.createQuery(jpql.toString(), CountryCityCode.class);
        if (keyword != null && !keyword.isBlank()) {
            query.setParameter("kw", "%" + keyword + "%");
        }
        if (type != null && !type.isBlank()) {
            query.setParameter("type", type);
        }
        return query.setMaxResults(20).getResultList();
    }

    @Override
    public List<CountryCityCode> findByType(String type) {
        return em.createQuery(
                        "SELECT c FROM CountryCityCode c WHERE c.type = :type ORDER BY c.name ASC", CountryCityCode.class)
                .setParameter("type", type)
                .getResultList();
    }

    @Override
    public List<CountryCityCode> findCitiesByCountryCodes(List<String> countryCodes) {
        if (countryCodes == null || countryCodes.isEmpty()) return List.of();
        return em.createQuery(
                        "SELECT c FROM CountryCityCode c WHERE c.type = 'city' AND c.countryCode IN :codes " +
                                "ORDER BY c.countryCode ASC, c.name ASC", CountryCityCode.class)
                .setParameter("codes", countryCodes)
                .getResultList();
    }
}

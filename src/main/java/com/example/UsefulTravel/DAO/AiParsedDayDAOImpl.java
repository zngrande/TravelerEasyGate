package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.AiParsedDay;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AiParsedDayDAOImpl implements AiParsedDayDAO {

    private final EntityManager em;

    @Autowired
    public AiParsedDayDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(AiParsedDay day) {
        em.persist(day);
    }

    @Override
    public List<AiParsedDay> findByImport(int IPID) {
        return em.createQuery(
                "SELECT d FROM AiParsedDay d WHERE d.IPID = :ipid ORDER BY d.dayNumber ASC", AiParsedDay.class)
                .setParameter("ipid", IPID)
                .getResultList();
    }
}

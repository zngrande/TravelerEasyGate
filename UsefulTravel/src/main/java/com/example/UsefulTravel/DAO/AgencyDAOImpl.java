package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.Agency;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class AgencyDAOImpl implements AgencyDAO {

    private final EntityManager em;

    @Autowired
    public AgencyDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(Agency agency) {
        if (agency.getCreatedAt() == null) {
            agency.setCreatedAt(LocalDateTime.now());
        }
        em.persist(agency);
    }

    @Override
    public Agency findById(int AID) {
        return em.find(Agency.class, AID);
    }
}

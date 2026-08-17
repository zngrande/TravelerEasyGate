package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.StaffUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class StaffUserDAOImpl implements StaffUserDAO {

    private final EntityManager em;

    @Autowired
    public StaffUserDAOImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void save(StaffUser staffUser) {
        if (staffUser.getUID() == 0) {
            if (staffUser.getCreatedAt() == null) {
                staffUser.setCreatedAt(LocalDateTime.now());
            }
            em.persist(staffUser);
        } else {
            em.merge(staffUser);
        }
    }

    @Override
    public StaffUser findByAccount(String account) {
        try {
            return em.createQuery("SELECT s FROM StaffUser s WHERE s.account = :account", StaffUser.class)
                    .setParameter("account", account)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public StaffUser findById(int UID) {
        return em.find(StaffUser.class, UID);
    }

    @Override
    public List<StaffUser> findByAgency(int AID) {
        return em.createQuery("SELECT s FROM StaffUser s WHERE s.AID = :aid", StaffUser.class)
                .setParameter("aid", AID)
                .getResultList();
    }
}

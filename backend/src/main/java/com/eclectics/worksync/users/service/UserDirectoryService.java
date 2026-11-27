package com.eclectics.worksync.users.service;

import com.eclectics.worksync.users.dto.UserSelection;
import com.eclectics.worksync.users.exception.PermissionTransactionRequiredException;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import com.eclectics.worksync.users.repository.UserRepository;
import java.sql.Connection;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Trusted Java lookup only. Projects must authorize directory/team access before invoking it. */
@Service
@Transactional(readOnly = true)
public class UserDirectoryService {
    private final UserRepository users;
    public UserDirectoryService(UserRepository users) { this.users = users; }

    public Page<UserSelection> searchActive(String search, Pageable page) {
        checkPage(page);
        return users.searchActive(normalize(search), PageRequest.of(page.getPageNumber(), page.getPageSize(),
                Sort.by(Sort.Order.asc("lastName").ignoreCase(), Sort.Order.asc("firstName").ignoreCase(), Sort.Order.asc("id"))));
    }
    /** Approved narrow read dependency on Projects' membership table; no public directory endpoint. */
    public Page<UserSelection> searchActiveNonMembers(UUID projectId, String search, Pageable page) {
        checkPage(page);
        return users.searchActiveNonMembers(Objects.requireNonNull(projectId), normalize(search),
                PageRequest.of(page.getPageNumber(), page.getPageSize()));
    }
    public Map<UUID, UserSelection> findSelections(Collection<UUID> ids) {
        if (ids.size() > 100) throw new IllegalArgumentException("Selection batch exceeds page size");
        Map<UUID, UserSelection> result = new HashMap<>();
        if (!ids.isEmpty()) users.findSelections(ids).forEach(u -> result.put(u.id(), u));
        return Map.copyOf(result);
    }
    public Optional<UserSelection> findActive(UUID id) {
        return users.findActiveSelection(Objects.requireNonNull(id));
    }
    /** Reuse V7's exact lifecycle lock before Projects row locks; no PROJECT_CREATE grant is needed.
     * The outer writable READ_COMMITTED membership transaction owns the lock through commit.
     * Scalar lookup avoids stale entity state and serializes against Users block/reject operations.
     */
    @Transactional(propagation = Propagation.MANDATORY, isolation = Isolation.READ_COMMITTED, readOnly = false)
    public void lockActiveActorForMembershipWrite(UUID actorId) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(Connection.TRANSACTION_READ_COMMITTED).equals(
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new PermissionTransactionRequiredException();
        }
        users.lockAccountLifecycle();
        if (users.findActiveSelection(actorId).isEmpty()) throw new UserNotFoundException();
    }
    private static void checkPage(Pageable page) {
        if (page == null || page.isUnpaged() || page.getPageNumber() < 0
                || page.getPageSize() < 1 || page.getPageSize() > 100) {
            throw new IllegalArgumentException("Bounded page required");
        }
        long offset = (long) page.getPageNumber() * page.getPageSize();
        if (offset > Integer.MAX_VALUE) throw new IllegalArgumentException("Unsupported page offset");
    }
    private static String normalize(String search) {
        if (search == null) return "";
        String value = search.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 100) throw new IllegalArgumentException("Search too long");
        return value;
    }
}

package com.eclectics.worksync.users.service;

import com.eclectics.worksync.users.dto.UserSelection;
import com.eclectics.worksync.users.repository.UserRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UserDirectoryServiceTests {
    final UserRepository repository=mock(UserRepository.class);
    final UserDirectoryService directory=new UserDirectoryService(repository);
    @Test void activeSearchNormalizesInputAndOverridesCallerSort() {
        when(repository.searchActive(anyString(),any())).thenReturn(Page.empty());
        directory.searchActive(" AbC ",PageRequest.of(2,5,Sort.by("passwordHash")));
        var page=org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchActive(eq("abc"),page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getSort().stream().map(Sort.Order::getProperty).toList()).containsExactly("lastName","firstName","id");
    }
    @Test void exclusionQueryIsScopedToProjectAndFixedSqlSort() {
        var id=UUID.randomUUID();when(repository.searchActiveNonMembers(any(),anyString(),any())).thenReturn(Page.empty());
        directory.searchActiveNonMembers(id," Name ",PageRequest.of(1,7,Sort.by("passwordHash")));
        verify(repository).searchActiveNonMembers(id,"name",PageRequest.of(1,7));
    }
    @Test void pageNameBatchIsRestrictedAndEmptyBatchSkipsDatabase() {
        assertThat(directory.findSelections(List.of())).isEmpty();verifyNoInteractions(repository);
        var id=UUID.randomUUID();var u=new UserSelection(id,"First","Last","test@example.com");
        when(repository.findSelections(List.of(id))).thenReturn(List.of(u));
        assertThat(directory.findSelections(List.of(id))).containsExactlyEntriesOf(Map.of(id,u));
        verify(repository).findSelections(List.of(id));
    }
    @Test void oversizedOrUnboundedRequestsNeverQueryDatabase() {
        assertThatThrownBy(() -> directory.searchActive("",Pageable.unpaged())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> directory.searchActive("",PageRequest.of(0,101))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> directory.searchActive("a".repeat(101),PageRequest.of(0,20))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> directory.findSelections(Collections.nCopies(101,UUID.randomUUID()))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }
    @Test void unsupportedOffsetsNeverReachEitherLookupQuery() {
        var page=PageRequest.of(Integer.MAX_VALUE,100);
        assertThatThrownBy(() -> directory.searchActive("",page)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> directory.searchActiveNonMembers(UUID.randomUUID(),"",page)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }
    @Test void maximumSupportedOffsetIsAcceptedForBothLookupPaths() {
        var page=PageRequest.of(Integer.MAX_VALUE,1);
        when(repository.searchActive(anyString(),any())).thenReturn(Page.empty(page));
        when(repository.searchActiveNonMembers(any(),anyString(),any())).thenReturn(Page.empty(page));
        assertThat(directory.searchActive("",page).getContent()).isEmpty();
        assertThat(directory.searchActiveNonMembers(UUID.randomUUID(),"",page).getContent()).isEmpty();
        verify(repository).searchActive(eq(""),any());
        verify(repository).searchActiveNonMembers(any(),eq(""),eq(page));
    }
}

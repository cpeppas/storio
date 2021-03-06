package com.pushtorefresh.storio.contentresolver.operations.delete;

import android.net.Uri;
import android.support.annotation.NonNull;

import com.pushtorefresh.storio.contentresolver.ContentResolverTypeMapping;
import com.pushtorefresh.storio.contentresolver.StorIOContentResolver;
import com.pushtorefresh.storio.test.ObservableBehaviorChecker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.functions.Action1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// stub class to avoid violation of DRY in tests
class DeleteObjectsStub {

    @NonNull
    final StorIOContentResolver storIOContentResolver;

    @NonNull
    private final StorIOContentResolver.Internal internal;

    @NonNull
    final List<TestItem> items;

    @NonNull
    final DeleteResolver<TestItem> deleteResolver;

    @NonNull
    private final ContentResolverTypeMapping<TestItem> typeMapping;

    @NonNull
    private final Map<TestItem, DeleteResult> testItemToDeleteResultMap;

    private final boolean withTypeMapping;

    @SuppressWarnings("unchecked")
    private DeleteObjectsStub(boolean withTypeMapping, int numberOfTestItems) {
        this.withTypeMapping = withTypeMapping;

        storIOContentResolver = mock(StorIOContentResolver.class);
        internal = mock(StorIOContentResolver.Internal.class);

        when(storIOContentResolver.internal())
                .thenReturn(internal);

        when(storIOContentResolver.delete())
                .thenReturn(new PreparedDelete.Builder(storIOContentResolver));

        deleteResolver = mock(DeleteResolver.class);

        items = new ArrayList<TestItem>(numberOfTestItems);
        testItemToDeleteResultMap = new HashMap<TestItem, DeleteResult>(numberOfTestItems);

        typeMapping = mock(ContentResolverTypeMapping.class);

        if (withTypeMapping) {
            when(typeMapping.deleteResolver()).thenReturn(deleteResolver);
            when(internal.typeMapping(TestItem.class)).thenReturn(typeMapping);
        }

        for (int i = 0; i < numberOfTestItems; i++) {
            final TestItem testItem = TestItem.newInstance();
            items.add(testItem);

            final Uri testItemUri = mock(Uri.class);

            final DeleteResult deleteResult = DeleteResult.newInstance(1, testItemUri);
            testItemToDeleteResultMap.put(testItem, deleteResult);

            when(deleteResolver.performDelete(storIOContentResolver, testItem))
                    .thenReturn(deleteResult);
        }
    }

    @NonNull
    static DeleteObjectsStub newInstanceForDeleteMultipleObjectsWithoutTypeMapping() {
        return new DeleteObjectsStub(false, 3);
    }

    @NonNull
    static DeleteObjectsStub newInstanceForDeleteMultipleObjectsWithTypeMapping() {
        return new DeleteObjectsStub(true, 3);
    }

    @NonNull
    static DeleteObjectsStub newInstanceForDeleteOneObjectWithoutTypeMapping() {
        return new DeleteObjectsStub(false, 1);
    }

    @NonNull
    static DeleteObjectsStub newInstanceForDeleteOneObjectWithTypeMapping() {
        return new DeleteObjectsStub(true, 1);
    }

    void verifyBehaviorForDeleteMultipleObjects(@NonNull DeleteResults<TestItem> deleteResults) {
        verify(storIOContentResolver).delete();

        if (withTypeMapping || items.size() > 1) {
            // should be called only once because of Performance!
            verify(storIOContentResolver).internal();
        }

        // checks that delete was performed same amount of times as count of items
        verify(deleteResolver, times(items.size())).performDelete(eq(storIOContentResolver), any(TestItem.class));

        for (final TestItem testItem : items) {
            // checks that delete was performed for each item
            verify(deleteResolver, times(1)).performDelete(storIOContentResolver, testItem);

            final DeleteResult expectedDeleteResult = testItemToDeleteResultMap.get(testItem);

            // checks that delete results contains result of deletion of each item
            assertThat(deleteResults.results().get(testItem)).isEqualTo(expectedDeleteResult);
        }

        assertThat(deleteResults.results()).hasSize(items.size());

        if (withTypeMapping) {
            verify(internal, times(items.size())).typeMapping(TestItem.class);
            verify(typeMapping, times(items.size())).deleteResolver();
        }

        verifyNoMoreInteractions(storIOContentResolver, internal, typeMapping, deleteResolver);
    }

    void verifyBehaviorForDeleteMultipleObjects(@NonNull Observable<DeleteResults<TestItem>> deleteResultsObservable) {
        new ObservableBehaviorChecker<DeleteResults<TestItem>>()
                .observable(deleteResultsObservable)
                .expectedNumberOfEmissions(1)
                .testAction(new Action1<DeleteResults<TestItem>>() {
                    @Override
                    public void call(DeleteResults<TestItem> deleteResults) {
                        verifyBehaviorForDeleteMultipleObjects(deleteResults);
                    }
                })
                .checkBehaviorOfObservable();
    }

    void verifyBehaviorForDeleteOneObject(@NonNull DeleteResult deleteResult) {
        Map<TestItem, DeleteResult> deleteResultsMap = new HashMap<TestItem, DeleteResult>(1);
        deleteResultsMap.put(items.get(0), deleteResult);
        verifyBehaviorForDeleteMultipleObjects(DeleteResults.newInstance(deleteResultsMap));
    }

    void verifyBehaviorForDeleteOneObject(@NonNull Observable<DeleteResult> deleteResultObservable) {
        new ObservableBehaviorChecker<DeleteResult>()
                .observable(deleteResultObservable)
                .expectedNumberOfEmissions(1)
                .testAction(new Action1<DeleteResult>() {
                    @Override
                    public void call(DeleteResult deleteResult) {
                        verifyBehaviorForDeleteOneObject(deleteResult);
                    }
                })
                .checkBehaviorOfObservable();
    }
}

package com.pushtorefresh.storio.contentresolver.operations.delete;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.pushtorefresh.storio.StorIOException;
import com.pushtorefresh.storio.contentresolver.ContentResolverTypeMapping;
import com.pushtorefresh.storio.contentresolver.StorIOContentResolver;
import com.pushtorefresh.storio.operations.internal.OnSubscribeExecuteAsBlocking;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.schedulers.Schedulers;

import static com.pushtorefresh.storio.internal.Environment.throwExceptionIfRxJavaIsNotAvailable;

/**
 * Prepared Delete Operation for {@link StorIOContentResolver}.
 *
 * @param <T> type of objects to delete.
 */
public final class PreparedDeleteCollectionOfObjects<T> extends PreparedDelete<DeleteResults<T>> {

    @NonNull
    private final Collection<T> objects;

    @Nullable
    private final DeleteResolver<T> explicitDeleteResolver;

    PreparedDeleteCollectionOfObjects(@NonNull StorIOContentResolver storIOContentResolver,
                                      @NonNull Collection<T> objects,
                                      @Nullable DeleteResolver<T> explicitDeleteResolver) {
        super(storIOContentResolver);
        this.objects = objects;
        this.explicitDeleteResolver = explicitDeleteResolver;
    }

    /**
     * Executes Delete Operation immediately in current thread.
     * <p>
     * Notice: This is blocking I/O operation that should not be executed on the Main Thread,
     * it can cause ANR (Activity Not Responding dialog), block the UI and drop animations frames.
     * So please, call this method on some background thread. See {@link WorkerThread}.
     *
     * @return non-null results of Delete Operation.
     */
    @SuppressWarnings("unchecked")
    @WorkerThread
    @NonNull
    @Override
    public DeleteResults<T> executeAsBlocking() {
        try {
            final StorIOContentResolver.Internal internal = storIOContentResolver.internal();

            // Nullable
            final List<SimpleImmutableEntry> objectsAndDeleteResolvers;

            if (explicitDeleteResolver != null) {
                objectsAndDeleteResolvers = null;
            } else {
                objectsAndDeleteResolvers = new ArrayList<SimpleImmutableEntry>(objects.size());

                for (final T object : objects) {
                    final ContentResolverTypeMapping<T> typeMapping
                            = (ContentResolverTypeMapping<T>) internal.typeMapping(object.getClass());

                    if (typeMapping == null) {
                        throw new IllegalStateException("One of the objects from the collection does not have type mapping: " +
                                "object = " + object + ", object.class = " + object.getClass() + "," +
                                "ContentProvider was not affected by this operation, please add type mapping for this type");
                    }

                    objectsAndDeleteResolvers.add(new SimpleImmutableEntry(
                            object,
                            typeMapping.deleteResolver()
                    ));
                }
            }

            final Map<T, DeleteResult> results = new HashMap<T, DeleteResult>(objects.size());

            if (explicitDeleteResolver != null) {
                for (final T object : objects) {
                    final DeleteResult deleteResult = explicitDeleteResolver.performDelete(storIOContentResolver, object);
                    results.put(object, deleteResult);
                }
            } else {
                for (final SimpleImmutableEntry<T, DeleteResolver<T>> objectAndDeleteResolver : objectsAndDeleteResolvers) {
                    final T object = objectAndDeleteResolver.getKey();
                    final DeleteResolver<T> deleteResolver = objectAndDeleteResolver.getValue();

                    final DeleteResult deleteResult = deleteResolver.performDelete(storIOContentResolver, object);
                    results.put(object, deleteResult);
                }
            }

            return DeleteResults.newInstance(results);

        } catch (Exception exception) {
            throw new StorIOException(exception);
        }
    }

    /**
     * Creates {@link Observable} which will perform Delete Operation and send result to observer.
     * <p>
     * Returned {@link Observable} will be "Cold Observable", which means that it performs
     * delete only after subscribing to it. Also, it emits the result once.
     * <p>
     * <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>Operates on {@link Schedulers#io()}.</dd>
     * </dl>
     *
     * @return non-null {@link Observable} which will perform Delete Operation.
     * and send result to observer.
     */
    @NonNull
    @Override
    public Observable<DeleteResults<T>> createObservable() {
        throwExceptionIfRxJavaIsNotAvailable("createObservable()");

        return Observable
                .create(OnSubscribeExecuteAsBlocking.newInstance(this))
                .subscribeOn(Schedulers.io());
    }

    /**
     * Builder for {@link PreparedDeleteCollectionOfObjects}.
     *
     * @param <T> type of objects.
     */
    public static final class Builder<T> {

        @NonNull
        private final StorIOContentResolver storIOContentResolver;

        @NonNull
        private final Collection<T> objects;

        @Nullable
        private DeleteResolver<T> deleteResolver;

        /**
         * Creates builder for {@link PreparedDeleteCollectionOfObjects}.
         *
         * @param storIOContentResolver non-null instance of {@link StorIOContentResolver}.
         * @param objects               non-null collection of objects to delete.
         */
        public Builder(@NonNull StorIOContentResolver storIOContentResolver, @NonNull Collection<T> objects) {
            this.storIOContentResolver = storIOContentResolver;
            this.objects = objects;
        }

        /**
         * Optional: Specifies resolver for Delete Operation.
         * Allows you to customise behavior of Delete Operation.
         * <p>
         * Can be set via {@link ContentResolverTypeMapping},
         * If value is not set via {@link ContentResolverTypeMapping}
         * or explicitly — exception will be thrown.
         *
         * @param deleteResolver nullable resolver for Delete Operation.
         * @return builder.
         */
        @NonNull
        public Builder<T> withDeleteResolver(@Nullable DeleteResolver<T> deleteResolver) {
            this.deleteResolver = deleteResolver;
            return this;
        }

        /**
         * Builds instance of {@link PreparedDeleteCollectionOfObjects}.
         *
         * @return instance of {@link PreparedDeleteCollectionOfObjects}.
         */
        @NonNull
        public PreparedDeleteCollectionOfObjects<T> prepare() {
            return new PreparedDeleteCollectionOfObjects<T>(
                    storIOContentResolver,
                    objects,
                    deleteResolver
            );
        }
    }
}

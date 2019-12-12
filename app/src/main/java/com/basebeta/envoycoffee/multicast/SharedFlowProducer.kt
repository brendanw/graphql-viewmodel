package com.basebeta.envoycoffee.multicast

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

/**
 * A flow collector that works with a [ChannelManager] to collect values from an upstream flow
 * and dispatch to the [ChannelManager] which then dispatches to downstream collectors.
 *
 * They work in sync such that this producer always expects an ack from the [ChannelManager] after
 * sending an event.
 *
 * Cancellation of the collection might be triggered by both this producer (e.g. upstream completes)
 * or the [ChannelManager] (e.g. all active collectors complete).
 */
@ExperimentalCoroutinesApi
class SharedFlowProducer<T>(
    private val scope: CoroutineScope,
    private val src: Flow<T>,
    private val channelManager: ChannelManager<T>
) {
    private lateinit var collectionJob: Job

    /**
     * Starts the collection of the upstream flow.
     */
    fun start() {
        scope.launch {
            try {
                // launch again to track the collection job
                collectionJob = scope.launch {
                    try {
                        src.catch {
                            channelManager.send(
                                ChannelManager.Message.DispatchError(
                                    it
                                )
                            )
                        }.collect {
                            val ack = CompletableDeferred<Unit>()
                            channelManager.send(
                                ChannelManager.Message.DispatchValue(
                                    it,
                                    ack
                                )
                            )
                            // suspend until at least 1 receives the new value
                            ack.await()
                        }
                    } catch (closed: ClosedSendChannelException) {
                        // ignore. if consumers are gone, it might close itself.
                    }
                }
                // wait until collection ends, either due to an error or ordered by the channel
                // manager
                collectionJob.join()
            } finally {
                // cleanup the channel manager so that downstreams can be closed if they are not
                // closed already and leftovers can be moved to a new producer if necessary.
                try {
                    channelManager.send(ChannelManager.Message.UpstreamFinished(this@SharedFlowProducer))
                } catch (closed : ClosedSendChannelException) {
                    // it might close before us, its fine.
                }
            }
        }
    }

    suspend fun cancelAndJoin() {
        collectionJob.cancelAndJoin()
    }

    fun cancel() {
        collectionJob.cancel()
    }
}

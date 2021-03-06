package io.charlag.moviesdbknot.logic

import io.charlag.moviesdbknot.data.models.Configuration
import io.charlag.moviesdbknot.data.network.Api
import io.charlag.redukt.Event
import io.charlag.redukt.createKnot
import io.charlag.redukt.epicOf
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

/**
 * Main logic module.
 * @author charlag
 * Date: 14/02/18
 */
interface Store {
  /**
   * Events which happen in the system. This includes events dispatched by the clientes as well as
   * events generated by the store itself.
   * @see dispatch
   */
  val events: Observable<Event>
  /**
   * Current state of the system. Updated on each event.
   */
  val state: Observable<State>

  /**
   * Submit new event to the store. Store may decide to act upon it.
   */
  fun dispatch(event: DispatchableEvent)
}

/**
 * Marker interface for all events which can be dispatched
 */
interface DispatchableEvent : Event

/**
 * State of the app
 */
data class State(
    /**
     * Current Configuration loaded from the API
     */
    val config: Configuration?,
    /**
     * Set of screens states in the backstack
     */
    val screens: List<ScreenState>
) {
  interface ScreenState
}

class StoreImpl(api: Api) : Store {

  override val events: Observable<Event>
  override val state: Observable<State>

  override fun dispatch(event: DispatchableEvent) {
    dispatchedEvents.onNext(event)
  }

  private val dispatchedEvents = PublishSubject.create<DispatchableEvent>()

  object InitEvent : Event

  fun reducer(state: State, event: Event): State {
    return state.copy(
        config = configReducer(state.config, event),
        screens = screensReducer(state.screens, event)
    )
  }

  init {
    val rootEpic = epicOf(
        discoverMoviesEpic(api),
        configurationEpic(api),
        navigateEpic,
        loadMovieDetailsEpic(api),
        finishAppEpic
    )

    val initialState = State(
        config = null,
        screens = listOf(
            DiscoverScreenState(0, listOf(), showError = false, isLoading = true,
                yearFilter = null))
    )

    val externalEvents: Observable<Event> = dispatchedEvents.cast(Event::class.java)
        .startWith(InitEvent)

    val (eventsKnot, state) = createKnot(
        initial = initialState,
        eventsSource = externalEvents,
        reducer = this::reducer,
        rootEpic = rootEpic
    )

    this.state = state
    events = eventsKnot

    eventsKnot.connect()
  }

}
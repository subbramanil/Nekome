package com.chesire.malime.view.manga

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.support.customtabs.CustomTabsIntent
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SimpleItemAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chesire.malime.R
import com.chesire.malime.mal.MalManager
import com.chesire.malime.models.Manga
import com.chesire.malime.models.UpdateManga
import com.chesire.malime.room.MalimeDatabase
import com.chesire.malime.room.MangaDao
import com.chesire.malime.util.SharedPref
import com.chesire.malime.view.MalModelInteractionListener
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class MangaFragment : Fragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    MalModelInteractionListener<Manga, UpdateManga> {

    private val mangaItemsBundleId = "mangaItems"

    private var disposables = CompositeDisposable()
    private lateinit var sharedPref: SharedPref
    private lateinit var username: String
    private lateinit var malManager: MalManager
    private lateinit var mangaDao: MangaDao

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: MangaViewAdapter
    private lateinit var viewManager: RecyclerView.LayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPref = SharedPref(context!!)
        username = sharedPref.getUsername()
        malManager = MalManager(sharedPref.getAuth())
        mangaDao = MalimeDatabase.getInstance(context!!).mangaDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_maldisplay, container, false)

        swipeRefreshLayout = view.findViewById(R.id.maldisplay_swipe_refresh)
        swipeRefreshLayout.setOnRefreshListener {
            executeGetLatestManga()
        }

        recyclerView = view.findViewById<RecyclerView>(R.id.maldisplay_recycler_view).apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }

        if (savedInstanceState == null) {
            executeGetLatestManga()
        } else {
            viewAdapter.addAll(savedInstanceState.getParcelableArrayList(mangaItemsBundleId))
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        disposables = CompositeDisposable()
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelableArrayList(mangaItemsBundleId, viewAdapter.getAll())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        sharedPref.unregisterOnChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onImageClicked(model: Manga) {
        CustomTabsIntent.Builder()
            .build()
            .launchUrl(context, Uri.parse(model.getMalUrl()))
    }

    override fun onLongClick(originalModel: Manga, updateModel: UpdateManga, callback: () -> Unit) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onSeriesUpdate(
        originalModel: Manga,
        updateModel: UpdateManga,
        callback: () -> Unit
    ) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun executeGetLocalManga() {
        Timber.d("Getting local manga")
        disposables.add(
            mangaDao.getAll()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Timber.d("Successfully got local manga, loading into adapter")
                    viewAdapter.addAll(it)
                })
        )
    }

    private fun executeGetLatestManga() {
        Timber.d("Getting latest manga from MAL")
        disposables.add(malManager.getAllManga(username)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    Timber.d("Successfully got latest manga from MAL")
                    executeSaveToLocalDb(result.second)
                    viewAdapter.addAll(result.second)
                    swipeRefreshLayout.isRefreshing = false
                },
                { _ ->
                    Timber.e("Failed to get latest manga from MAL")
                    swipeRefreshLayout.isRefreshing = false
                }
            ))
    }

    private fun executeUpdateMal(originalModel: Manga, model: UpdateManga, callback: () -> Unit) {
        Timber.d("Sending update to MAL for manga - [%s]", originalModel)

        disposables.add(malManager.updateManga(model)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { _ ->
                    Timber.d("Successfully updated manga on MAL")

                    callback()
                    executeUpdateInLocalDb(originalModel)
                    viewAdapter.updateItem(model)
                },
                { _ ->
                    Timber.e("Error trying to update manga on MAL")

                    callback()
                    Snackbar.make(
                        recyclerView,
                        String.format(
                            getString(R.string.malitem_update_series_failure),
                            model.title
                        ), Snackbar.LENGTH_LONG
                    ).show()
                }
            ))
    }

    private fun executeSaveToLocalDb(mangas: List<Manga>) {
        Timber.d("Updating local DB for all manga")

        // Looks like this doesn't have to be disposed off
        Completable
            .fromAction({
                mangaDao.insertAll(mangas)
            })
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    private fun executeUpdateInLocalDb(manga: Manga) {
        Timber.d("Updating local DB for manga - [%s]", manga)

        // Looks like this doesn't have to be disposed off
        Completable
            .fromAction({
                mangaDao.update(manga)
            })
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    companion object {
        const val tag = "MangaFragment"
        fun newInstance(): MangaFragment {
            val mangaFragment = MangaFragment()
            val args = Bundle()
            mangaFragment.arguments = args
            return mangaFragment
        }
    }
}
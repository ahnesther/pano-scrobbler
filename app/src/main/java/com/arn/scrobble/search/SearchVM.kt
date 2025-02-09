package com.arn.scrobble.search

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arn.scrobble.LFMRequester
import com.arn.scrobble.ui.SectionedVirtualList
import de.umass.lastfm.Album
import de.umass.lastfm.Artist
import de.umass.lastfm.Track


class SearchVM : ViewModel() {
    val searchResults by lazy { MutableLiveData<SearchResults>() }
    val virtualList = SectionedVirtualList()
    private var searchJob: LFMRequester? = null

    fun loadSearches(term: String, searchType: SearchResultsAdapter.SearchType) {
        searchJob?.cancel()
        searchJob = LFMRequester(viewModelScope, searchResults).apply {
            when (searchType) {
                SearchResultsAdapter.SearchType.GLOBAL -> getSearches(term)
                SearchResultsAdapter.SearchType.LOCAL -> getLocalSearches(term)
            }
        }
    }

    class SearchResults(
        val term: String,
        val searchType: SearchResultsAdapter.SearchType,
        val lovedTracks: List<Track>,
        val tracks: List<Track>,
        val artists: List<Artist>,
        val albums: List<Album>,
    ) {
        val isEmpty: Boolean
            get() = lovedTracks.isEmpty() && tracks.isEmpty() && artists.isEmpty() && albums.isEmpty()
    }
}
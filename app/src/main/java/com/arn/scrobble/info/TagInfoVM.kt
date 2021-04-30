package com.arn.scrobble.info

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.arn.scrobble.LFMRequester
import de.umass.lastfm.Tag


class TagInfoVM(app: Application): AndroidViewModel(app) {
    val info = MutableLiveData<Pair<Tag?, List<Tag>?>>()

    fun loadInfo(tag: String) {
        LFMRequester(getApplication()).getTagInfo(tag).asAsyncTask(info)
    }
}
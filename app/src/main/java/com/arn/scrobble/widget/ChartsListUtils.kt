package com.arn.scrobble.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.arn.scrobble.App
import com.arn.scrobble.NLService
import com.arn.scrobble.R
import com.arn.scrobble.Stuff
import com.arn.scrobble.Stuff.putSingle
import com.arn.scrobble.pref.WidgetPrefs
import com.arn.scrobble.scrobbleable.Scrobblables
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.NumberFormat

object ChartsListUtils {

    fun createHeader(prefs: WidgetPrefs.SpecificWidgetPrefs): RemoteViews {
        val headerView = RemoteViews(App.context.packageName, R.layout.appwidget_list_header)
        headerView.setTextViewText(R.id.appwidget_period, prefs.periodName)
        return headerView
    }

    fun createMusicItem(tab: Int, idx: Int, item: ChartsWidgetListItem): RemoteViews {
        val rv = RemoteViews(App.context.packageName, R.layout.appwidget_charts_item)
        rv.setTextViewText(
            R.id.appwidget_charts_serial, NumberFormat.getInstance().format(idx + 1) + "."
        )
        rv.setTextViewText(R.id.appwidget_charts_title, item.title)
        rv.setImageViewResource(
            R.id.appwidget_charts_stonks_icon, Stuff.stonksIconForDelta(item.stonksDelta)
        )
        rv.setImageViewResource(
            R.id.appwidget_charts_stonks_icon_shadow, Stuff.stonksIconForDelta(item.stonksDelta)
        )
        rv.setContentDescription(R.id.appwidget_charts_stonks_icon, item.stonksDelta.toString())

        if (item.subtitle != "") {
            rv.setTextViewText(R.id.appwidget_charts_subtitle, item.subtitle)
            rv.setViewVisibility(R.id.appwidget_charts_subtitle, View.VISIBLE)
        } else rv.setViewVisibility(R.id.appwidget_charts_subtitle, View.GONE)

        rv.setTextViewText(
            R.id.appwidget_charts_plays, NumberFormat.getInstance().format(item.number)
        )
        // Next, we set a fill-intent which will be used to fill-in the pending intent template
        // which is set on the collection view in StackWidgetProvider.
        val fillInIntent = Intent().apply {
            when (tab) {
                Stuff.TYPE_ARTISTS -> {
                    putExtra(NLService.B_ARTIST, item.title)
                }

                Stuff.TYPE_ALBUMS -> {
                    putExtra(NLService.B_ARTIST, item.subtitle)
                    putExtra(NLService.B_ALBUM, item.title)
                }

                Stuff.TYPE_TRACKS -> {
                    putExtra(NLService.B_ARTIST, item.subtitle)
                    putExtra(NLService.B_TRACK, item.title)
                }
            }
            putSingle(Scrobblables.current?.userAccount?.user ?: return@apply)
            putExtra(Stuff.ARG_INFO, true)
        }
        rv.setOnClickFillInIntent(R.id.appwidget_charts_item, fillInIntent)
        return rv
    }

    fun readList(prefs: WidgetPrefs.SpecificWidgetPrefs): ArrayList<ChartsWidgetListItem> {
        val tab = prefs.tab ?: Stuff.TYPE_ARTISTS
        val period = prefs.period ?: return arrayListOf()

        return kotlin.runCatching {
            Json.decodeFromString<ArrayList<ChartsWidgetListItem>>(
                prefs.sharedPreferences.getString("${tab}_$period", null)!!
            )
        }.getOrDefault(arrayListOf())
    }


    fun updateWidget(appWidgetId: Int) {
        val i = Intent(App.context, ChartsWidgetProvider::class.java).apply {
            action = NLService.iUPDATE_WIDGET
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        App.context.sendBroadcast(i)
    }
}
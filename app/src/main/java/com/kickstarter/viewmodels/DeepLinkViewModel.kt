package com.kickstarter.viewmodels

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.util.Pair
import com.kickstarter.libs.ActivityViewModel
import com.kickstarter.libs.CurrentUserType
import com.kickstarter.libs.Environment
import com.kickstarter.libs.RefTag
import com.kickstarter.libs.rx.transformers.Transformers
import com.kickstarter.libs.utils.ObjectUtils
import com.kickstarter.libs.utils.Secrets
import com.kickstarter.libs.utils.UrlUtils.appendRefTag
import com.kickstarter.libs.utils.UrlUtils.refTag
import com.kickstarter.libs.utils.extensions.isCheckoutUri
import com.kickstarter.libs.utils.extensions.isDiscoverPath
import com.kickstarter.libs.utils.extensions.isDiscoverSortParam
import com.kickstarter.libs.utils.extensions.isProjectPreviewUri
import com.kickstarter.libs.utils.extensions.isProjectUri
import com.kickstarter.libs.utils.extensions.isSettingsUrl
import com.kickstarter.models.User
import com.kickstarter.services.ApiClientType
import com.kickstarter.ui.activities.DeepLinkActivity
import rx.Notification
import rx.Observable
import rx.subjects.BehaviorSubject
import timber.log.Timber

interface DeepLinkViewModel {
    interface Outputs {
        /** Emits when we should start an external browser because we don't want to deep link.  */
        fun startBrowser(): Observable<String>

        /** Emits when we should start the [com.kickstarter.ui.activities.DiscoveryActivity].  */
        fun startDiscoveryActivity(): Observable<Uri>

        /** Emits when we should start the [com.kickstarter.ui.activities.ProjectActivity].  */
        fun startProjectActivity(): Observable<Uri>

        /** Emits when we should start the [com.kickstarter.ui.activities.ProjectActivity] with pledge sheet expanded.  */
        fun startProjectActivityForCheckout(): Observable<Uri>

        /** Emits when we should finish the current activity  */
        fun finishDeeplinkActivity(): Observable<Void>
    }

    class ViewModel(environment: Environment) :
        ActivityViewModel<DeepLinkActivity?>(environment), Outputs {

        private val startBrowser = BehaviorSubject.create<String>()
        private val startDiscoveryActivity = BehaviorSubject.create<Uri>()
        private val startProjectActivity = BehaviorSubject.create<Uri>()
        private val startProjectActivityWithCheckout = BehaviorSubject.create<Uri>()
        private val updateUserPreferences = BehaviorSubject.create<Boolean>()
        private val finishDeeplinkActivity = BehaviorSubject.create<Void?>()
        val outputs: Outputs = this

        init {
            val apiClientType = environment.apiClient()
            val currentUser = environment.currentUser()
            val uriFromIntent = intent()
                .map { obj: Intent -> obj.data }
                .ofType(Uri::class.java)

            uriFromIntent
                .compose(bindToLifecycle())
                .subscribe { Timber.d("leigh%s", it.toString()) }

            uriFromIntent
                .filter { it.isDiscoverSortParam(Secrets.WebEndpoint.PRODUCTION) }
                .compose(bindToLifecycle())
                .subscribe {
                    Timber.d("leigh is awesome")
                    startDiscoveryActivity.onNext(it)
                }

            uriFromIntent
                .filter { ObjectUtils.isNotNull(it) }
                .filter {
                    it.isProjectUri(Secrets.WebEndpoint.PRODUCTION)
                }
                .filter {
                    !it.isCheckoutUri(Secrets.WebEndpoint.PRODUCTION)
                }
                .filter {
                    !it.isProjectPreviewUri(Secrets.WebEndpoint.PRODUCTION)
                }
                .map { appendRefTagIfNone(it) }
                .compose(bindToLifecycle())
                .subscribe {
                    startProjectActivity.onNext(it)
                }

            uriFromIntent
                .filter { ObjectUtils.isNotNull(it) }
                .map { it.isSettingsUrl() }
                .compose(bindToLifecycle())
                .subscribe {
                    updateUserPreferences.onNext(it)
                }

            currentUser.observable()
                .filter { ObjectUtils.isNotNull(it) }
                .compose(Transformers.combineLatestPair(updateUserPreferences))
                .switchMap { it: Pair<User, Boolean?> ->
                    updateSettings(it.first, apiClientType)
                }
                .compose(Transformers.values())
                .distinctUntilChanged()
                .filter { ObjectUtils.isNotNull(it) }
                .map { requireNotNull(it) }
                .compose(bindToLifecycle())
                .subscribe {
                    refreshUserAndFinishActivity(it, currentUser)
                }

            uriFromIntent
                .filter { ObjectUtils.isNotNull(it) }
                .filter { it.isCheckoutUri(Secrets.WebEndpoint.PRODUCTION) }
                .map { appendRefTagIfNone(it) }
                .compose(bindToLifecycle())
                .subscribe {
                    startProjectActivityWithCheckout.onNext(it)
                }

            val projectPreview = uriFromIntent
                .filter { ObjectUtils.isNotNull(it) }
                .filter { it.isProjectPreviewUri(Secrets.WebEndpoint.PRODUCTION) }

            val unsupportedDeepLink = uriFromIntent
                .filter { !lastPathSegmentIsProjects(it) }
                .filter { !it.isSettingsUrl() }
                .filter { !it.isCheckoutUri(Secrets.WebEndpoint.PRODUCTION) }
                .filter { !it.isDiscoverPath(Secrets.WebEndpoint.PRODUCTION) }
                .filter { !it.isProjectUri(Secrets.WebEndpoint.PRODUCTION) }

            Observable.merge(projectPreview, unsupportedDeepLink)
                .map { obj: Uri -> obj.toString() }
                .filter { !TextUtils.isEmpty(it) }
                .compose(bindToLifecycle())
                .subscribe {
                    startBrowser.onNext(it)
                }
        }

        private fun refreshUserAndFinishActivity(user: User, currentUser: CurrentUserType) {
            currentUser.refresh(user)
            finishDeeplinkActivity.onNext(null)
        }

        private fun appendRefTagIfNone(uri: Uri): Uri {
            val url = uri.toString()
            val ref = refTag(url)
            return if (ObjectUtils.isNull(ref)) {
                Uri.parse(appendRefTag(url, RefTag.deepLink().tag()))
            } else uri
        }

        private fun lastPathSegmentIsProjects(uri: Uri): Boolean {
            return uri.lastPathSegment == "projects"
        }

        private fun updateSettings(
            user: User,
            apiClientType: ApiClientType
        ): Observable<Notification<User?>?> {
            val updatedUser = user.toBuilder().notifyMobileOfMarketingUpdate(true).build()
            return apiClientType.updateUserSettings(updatedUser)
                .materialize()
                .share()
        }

        override fun startBrowser(): Observable<String> = startBrowser

        override fun startDiscoveryActivity(): Observable<Uri> = startDiscoveryActivity

        override fun startProjectActivity(): Observable<Uri> = startProjectActivity

        override fun startProjectActivityForCheckout(): Observable<Uri> = startProjectActivityWithCheckout

        override fun finishDeeplinkActivity(): Observable<Void> = finishDeeplinkActivity
    }
}

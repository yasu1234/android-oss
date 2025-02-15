package com.kickstarter.viewmodels

import com.kickstarter.libs.ActivityViewModel
import com.kickstarter.libs.Environment
import com.kickstarter.libs.models.OptimizelyFeature
import com.kickstarter.libs.rx.transformers.Transformers
import com.kickstarter.libs.rx.transformers.Transformers.errors
import com.kickstarter.libs.rx.transformers.Transformers.values
import com.kickstarter.libs.utils.ObjectUtils
import com.kickstarter.libs.utils.extensions.isEmail
import com.kickstarter.models.User
import com.kickstarter.services.apiresponses.ErrorEnvelope
import com.kickstarter.ui.IntentKey
import com.kickstarter.ui.activities.ResetPasswordActivity
import com.kickstarter.ui.data.ResetPasswordScreenState
import rx.Notification
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject

interface ResetPasswordViewModel {

    interface Inputs {
        /** Call when the email field changes. */
        fun email(emailInput: String)

        /** Call when the reset password button is clicked. */
        fun resetPasswordClick()
    }

    interface Outputs {
        /** Emits a boolean that determines if the form is in the progress of being submitted. */
        fun isFormSubmitting(): Observable<Boolean>

        /** Emits a boolean that determines if the form validation is passing. */
        fun isFormValid(): Observable<Boolean>

        /** Emits when password reset is completed successfully. */
        fun resetLoginPasswordSuccess(): Observable<Void>

        /** Emits when password reset is completed successfully. */
        fun resetFacebookLoginPasswordSuccess(): Observable<Void>

        /** Emits when password reset fails. */
        fun resetError(): Observable<String>

        /** Fill the view's email address when it's supplied from the intent.  */
        fun prefillEmail(): Observable<String>

        /** Fill the view's for forget or reset password state   */
        fun resetPasswordScreenStatus(): Observable<ResetPasswordScreenState>
    }

    class ViewModel(val environment: Environment) : ActivityViewModel<ResetPasswordActivity>(environment), Inputs, Outputs {
        private val client = requireNotNull(environment.apiClient())

        private val email = PublishSubject.create<String>()
        private val resetPasswordClick = PublishSubject.create<Void>()

        private val isFormSubmitting = PublishSubject.create<Boolean>()
        private val isFormValid = PublishSubject.create<Boolean>()
        private val resetLoginPasswordSuccess = PublishSubject.create<Void>()
        private val resetFacebookLoginPasswordSuccess = PublishSubject.create<Void>()
        private val resetError = PublishSubject.create<ErrorEnvelope>()
        private val prefillEmail = BehaviorSubject.create<String>()
        private val resetPasswordScreenStatus = BehaviorSubject.create<ResetPasswordScreenState>()

        private val ERROR_GENERIC = "Something went wrong, please try again."

        val inputs: Inputs = this
        val outputs: Outputs = this

        // TODO removed with feature flag ANDROID_FACEBOOK_LOGIN_REMOVE
        private var resetPasswordScreenState: ResetPasswordScreenState? = null

        init {

            intent()
                .filter { it.hasExtra(IntentKey.EMAIL) }
                .map {
                    it.getStringExtra(IntentKey.EMAIL)
                }
                .filter { ObjectUtils.isNotNull(it) }
                .map { requireNotNull(it) }
                .compose(bindToLifecycle())
                .subscribe {
                    this.prefillEmail.onNext(it)
                    resetPasswordScreenState = ResetPasswordScreenState.ForgetPassword
                    resetPasswordScreenStatus.onNext(ResetPasswordScreenState.ForgetPassword)
                }

            val resetFacebookPasswordFlag = intent()
                .filter {
                    it.hasExtra(IntentKey.RESET_PASSWORD_FACEBOOK_LOGIN) && environment.optimizely()?.isFeatureEnabled(
                        OptimizelyFeature.Key.ANDROID_FACEBOOK_LOGIN_REMOVE
                    ) == true
                }
                .map {
                    it.getBooleanExtra(IntentKey.RESET_PASSWORD_FACEBOOK_LOGIN, false)
                }

            resetFacebookPasswordFlag
                .compose(bindToLifecycle())
                .subscribe {
                    if (it) {
                        resetPasswordScreenState = ResetPasswordScreenState.ResetPassword
                        resetPasswordScreenStatus.onNext(ResetPasswordScreenState.ResetPassword)
                    } else {
                        resetPasswordScreenState = ResetPasswordScreenState.ForgetPassword
                        resetPasswordScreenStatus.onNext(ResetPasswordScreenState.ForgetPassword)
                    }
                }

            this.email
                .map { it.isEmail() }
                .compose(bindToLifecycle())
                .subscribe(this.isFormValid)

            val resetPasswordNotification = this.email
                .compose<String>(Transformers.takeWhen(this.resetPasswordClick))
                .switchMap(this::submitEmail)
                .share()

            resetPasswordNotification
                .compose(values())
                .compose(bindToLifecycle())
                .subscribe {
                    when (resetPasswordScreenState) {
                        ResetPasswordScreenState.ResetPassword -> resetFacebookLoginPasswordSuccess.onNext(
                            null
                        )
                        else -> success()
                    }
                }

            resetPasswordNotification
                .compose(errors())
                .map { ErrorEnvelope.fromThrowable(it) }
                .compose(bindToLifecycle())
                .subscribe(this.resetError)
        }

        private fun success() {
            this.resetLoginPasswordSuccess.onNext(null)
        }

        private fun submitEmail(email: String): Observable<Notification<User>> {
            return this.client.resetPassword(email)
                .doOnSubscribe { this.isFormSubmitting.onNext(true) }
                .doAfterTerminate { this.isFormSubmitting.onNext(false) }
                .materialize()
                .share()
        }

        override fun email(emailInput: String) {
            this.email.onNext(emailInput)
        }

        override fun resetPasswordClick() {
            this.resetPasswordClick.onNext(null)
        }

        override fun isFormSubmitting(): Observable<Boolean> {
            return this.isFormSubmitting
        }

        override fun isFormValid(): Observable<Boolean> {
            return this.isFormValid
        }

        override fun resetLoginPasswordSuccess(): Observable<Void> {
            return this.resetLoginPasswordSuccess
        }

        override fun resetFacebookLoginPasswordSuccess(): Observable<Void> {
            return this.resetFacebookLoginPasswordSuccess
        }

        override fun resetError(): Observable<String> {
            return this.resetError
                .takeUntil(this.resetLoginPasswordSuccess)
                .map { it?.errorMessage() ?: ERROR_GENERIC }
        }

        override fun prefillEmail(): BehaviorSubject<String> = this.prefillEmail

        override fun resetPasswordScreenStatus(): Observable<ResetPasswordScreenState> = this.resetPasswordScreenStatus
    }
}

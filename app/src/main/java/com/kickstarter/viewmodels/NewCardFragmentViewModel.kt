package com.kickstarter.viewmodels

import android.util.Pair
import androidx.annotation.NonNull
import com.kickstarter.R
import com.kickstarter.libs.Environment
import com.kickstarter.libs.FragmentViewModel
import com.kickstarter.libs.models.Country
import com.kickstarter.libs.rx.transformers.Transformers.combineLatestPair
import com.kickstarter.libs.rx.transformers.Transformers.errors
import com.kickstarter.libs.rx.transformers.Transformers.ignoreValues
import com.kickstarter.libs.rx.transformers.Transformers.takeWhen
import com.kickstarter.libs.rx.transformers.Transformers.values
import com.kickstarter.libs.utils.ObjectUtils
import com.kickstarter.libs.utils.extensions.negate
import com.kickstarter.models.Project
import com.kickstarter.models.StoredCard
import com.kickstarter.services.mutations.SavePaymentMethodData
import com.kickstarter.ui.ArgumentsKey
import com.kickstarter.ui.fragments.NewCardFragment
import com.stripe.android.model.CardBrand
import com.stripe.android.model.CardParams
import com.stripe.android.model.Token
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject

interface NewCardFragmentViewModel {
    interface Inputs {
        /** Call when the card validity changes. */
        fun card(cardParams: CardParams?)

        /** Call when the card input has focus. */
        fun cardFocus(hasFocus: Boolean)

        /** Call when the card number text changes. */
        fun cardNumber(cardNumber: String)

        /** Call when the name field changes. */
        fun name(name: String)

        /** Call when the postal code field changes. */
        fun postalCode(postalCode: String)

        /** Call when the reusable switch is toggled. */
        fun reusable(reusable: Boolean)

        /** Call when the user clicks the save icon. */
        fun saveCardClicked()

        /** Call when Stripe token creation is successful. */
        fun stripeTokenResultSuccessful(token: Token)

        /** Call when Stripe token creation is unsuccessful. */
        fun stripeTokenResultUnsuccessful(exception: Exception)
    }

    interface Outputs {
        /** Emits a string resource and project to display warning. */
        fun allowedCardWarning(): Observable<Pair<Int?, Project?>>

        /** Emits a boolean determining if the allowed card warning should be visible. */
        fun allowedCardWarningIsVisible(): Observable<Boolean>

        /** Emits a boolean determining if the AppBarLayout should have elevation. */
        fun appBarLayoutHasElevation(): Observable<Boolean>

        /** Emits a drawable to be shown based on when the card widget has focus. */
        fun cardWidgetFocusDrawable(): Observable<Int>

        /** Emits when we should create a Stripe token using card. */
        fun createStripeToken(): Observable<CardParams>

        /** Emits a boolean determining if the form divider should be visible. */
        fun dividerIsVisible(): Observable<Boolean>

        /** Emits when saving the card was unsuccessful and the fragment is not modal. */
        fun error(): Observable<Void>

        /** Emits when saving the card was unsuccessful and the fragment is modal. */
        fun modalError(): Observable<Void>

        /** Emits when the progress bar should be visible. */
        fun progressBarIsVisible(): Observable<Boolean>

        /** Emits a boolean determining if the reusable switch should be visible. */
        fun reusableContainerIsVisible(): Observable<Boolean>

        /** Emits a boolean determining if the save button should be enabled. */
        fun saveButtonIsEnabled(): Observable<Boolean>

        /** Emits when the card was saved successfully. */
        fun success(): Observable<StoredCard>
    }

    class ViewModel(@NonNull val environment: Environment) : FragmentViewModel<NewCardFragment>(environment), Inputs, Outputs {

        private val cardParams = PublishSubject.create<CardParams>()
        private val cardFocus = PublishSubject.create<Boolean>()
        private val cardNumber = PublishSubject.create<String>()
        private val name = PublishSubject.create<String>()
        private val postalCode = PublishSubject.create<String>()
        private val reusable = PublishSubject.create<Boolean>()
        private val saveCardClicked = PublishSubject.create<Void>()
        private val stripeTokenResultSuccessful = PublishSubject.create<Token>()
        private val stripeTokenResultUnsuccessful = PublishSubject.create<Exception>()

        private val allowedCardWarning = BehaviorSubject.create<Pair<Int?, Project?>>()
        private val allowedCardWarningIsVisible = BehaviorSubject.create<Boolean>()
        private val appBarLayoutHasElevation = BehaviorSubject.create<Boolean>()
        private val cardWidgetFocusDrawable = BehaviorSubject.create<Int>()
        private val createStripeToken = PublishSubject.create<CardParams>()
        private val dividerIsVisible = BehaviorSubject.create<Boolean>()
        private val error = BehaviorSubject.create<Void>()
        private val modalError = BehaviorSubject.create<Void>()
        private val progressBarIsVisible = BehaviorSubject.create<Boolean>()
        private val reusableContainerIsVisible = BehaviorSubject.create<Boolean>()
        private val saveButtonIsEnabled = BehaviorSubject.create<Boolean>()
        private val success = BehaviorSubject.create<StoredCard>()

        val inputs: Inputs = this
        val outputs: Outputs = this

        private val apolloClient = requireNotNull(this.environment.apolloClient())

        init {
            val modal = arguments()
                .map { it?.getBoolean(ArgumentsKey.NEW_CARD_MODAL) ?: false }
                .distinctUntilChanged()

            val project = arguments()
                .map<Project?> { it?.getParcelable(ArgumentsKey.NEW_CARD_PROJECT) ?: null }
                .distinctUntilChanged()

            modal
                .map { it.negate() }
                .compose(bindToLifecycle())
                .subscribe(this.appBarLayoutHasElevation)

            modal
                .map { it.negate() }
                .compose(bindToLifecycle())
                .subscribe(this.dividerIsVisible)

            modal
                .compose(bindToLifecycle())
                .subscribe(this.reusableContainerIsVisible)

            val initialReusable = modal
                .map { it.negate() }

            val reusable = Observable.merge(initialReusable, this.reusable)

            val cardForm = Observable.combineLatest(
                this.name,
                this.cardParams,
                this.cardNumber,
                this.postalCode,
                reusable
            ) { name, card, cardNumber, postalCode, reusable -> CardForm(name, card, cardNumber, postalCode, reusable) }

            cardForm
                .compose<Pair<CardForm, Project?>>(combineLatestPair(project))
                .map { it.first.isValid(it.second) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.saveButtonIsEnabled)

            val warning = this.cardParams
                .filter { ObjectUtils.isNotNull(it) }
                .compose<Pair<CardParams, Project?>>(combineLatestPair(project))
                .map<Pair<Int?, Project?>> { Pair(CardForm.warning(it.first, it.second), it.second) }
                .distinctUntilChanged()

            warning
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.allowedCardWarning)

            warning
                .map { ObjectUtils.isNotNull(it.first) }
                .distinctUntilChanged()
                .compose(bindToLifecycle())
                .subscribe(this.allowedCardWarningIsVisible)

            this.allowedCardWarningIsVisible
                .startWith(false)
                .distinctUntilChanged()
                .compose<Pair<Boolean, Boolean>>(combineLatestPair(this.cardFocus.startWith(false).distinctUntilChanged()))
                .map {
                    val cardNotAllowed = it.first
                    val hasFocus = it.second
                    when {
                        cardNotAllowed -> R.drawable.divider_red_400_horizontal
                        hasFocus -> R.drawable.divider_green_horizontal
                        else -> R.drawable.divider_dark_grey_500_horizontal
                    }
                }
                .distinctUntilChanged()
                .subscribe {
                    this.cardWidgetFocusDrawable.onNext(it)
                }

            cardForm
                .map { it.cardParams }
                .filter { ObjectUtils.isNotNull(it) }
                .compose<CardParams>(takeWhen(this.saveCardClicked))
                .compose(bindToLifecycle())
                .subscribe {
                    this.createStripeToken.onNext(it)
                    this.progressBarIsVisible.onNext(true)
                }

            val saveCardNotification = this.stripeTokenResultSuccessful
                .map { token -> token.card?.id?.let { Pair(token.id, it) } }
                .compose<Pair<Pair<String, String>, Boolean>>(combineLatestPair(reusable))
                .map { SavePaymentMethodData(stripeToken = it.first.first, stripeCardId = it.first.second, reusable = it.second) }
                .switchMap { this.apolloClient.savePaymentMethod(it).materialize() }
                .share()

            saveCardNotification
                .compose(values())
                .compose(bindToLifecycle())
                .subscribe { this.success.onNext(it) }

            val errors = Observable.merge(saveCardNotification.compose(errors()), this.stripeTokenResultUnsuccessful)
                .compose(ignoreValues())

            val errorsAndModal = errors
                .compose<Pair<Void, Boolean>>(combineLatestPair(modal))

            errorsAndModal
                .filter { !it.second }
                .map { it.first }
                .subscribe(this.error)

            errorsAndModal
                .filter { it.second }
                .map { it.first }
                .subscribe(this.modalError)

            errors.subscribe { this.progressBarIsVisible.onNext(false) }
        }

        override fun card(cardParams: CardParams?) {
            this.cardParams.onNext(cardParams)
        }

        override fun cardFocus(hasFocus: Boolean) {
            this.cardFocus.onNext(hasFocus)
        }

        override fun cardNumber(cardNumber: String) {
            this.cardNumber.onNext(cardNumber)
        }

        override fun name(name: String) {
            this.name.onNext(name)
        }

        override fun postalCode(postalCode: String) {
            this.postalCode.onNext(postalCode)
        }

        override fun reusable(reusable: Boolean) {
            this.reusable.onNext(reusable)
        }

        override fun saveCardClicked() {
            this.saveCardClicked.onNext(null)
        }

        override fun stripeTokenResultSuccessful(token: Token) {
            this.stripeTokenResultSuccessful.onNext(token)
        }

        override fun stripeTokenResultUnsuccessful(exception: Exception) {
            this.stripeTokenResultUnsuccessful.onNext(exception)
        }

        override fun allowedCardWarning(): Observable<Pair<Int?, Project?>> = this.allowedCardWarning

        override fun allowedCardWarningIsVisible(): Observable<Boolean> = this.allowedCardWarningIsVisible

        override fun appBarLayoutHasElevation(): Observable<Boolean> = this.appBarLayoutHasElevation

        override fun cardWidgetFocusDrawable(): Observable<Int> = this.cardWidgetFocusDrawable

        override fun createStripeToken(): Observable<CardParams> = this.createStripeToken

        override fun dividerIsVisible(): Observable<Boolean> = this.dividerIsVisible

        override fun error(): Observable<Void> = this.error

        override fun modalError(): Observable<Void> = this.modalError

        override fun progressBarIsVisible(): Observable<Boolean> = this.progressBarIsVisible

        override fun reusableContainerIsVisible(): Observable<Boolean> = this.reusableContainerIsVisible

        override fun saveButtonIsEnabled(): Observable<Boolean> = this.saveButtonIsEnabled

        override fun success(): Observable<StoredCard> = this.success

        data class CardForm(val name: String, val cardParams: CardParams?, val cardNumber: String, val postalCode: String, val reusable: Boolean) {

            fun isValid(project: Project?): Boolean {
                return this.name.isNotEmpty() &&
                    this.postalCode.isNotEmpty() &&
                    isValidCard(project)
            }

            private fun isValidCard(project: Project?): Boolean {
                return this.cardParams != null && warning(this.cardParams, project) == null
            }

            companion object {
                fun warning(params: CardParams, project: Project?): Int? {
                    return if (params.last4.length < 3)
                        -1
                    else {
                        when (project) {
                            null -> when (params.brand.code) {
                                in allowedCardTypes -> null
                                else -> R.string.Unsupported_card_type
                            }
                            else -> when (params.brand.code) {
                                in getAllowedTypes(project) -> null
                                else -> R.string.You_cant_use_this_credit_card_to_back_a_project_from_project_country
                            }
                        }
                    }
                }

                private fun getAllowedTypes(project: Project): Array<String> {
                    return when {
                        project.currency() == Country.US.currencyCode -> usdCardTypes
                        else -> nonUsdCardTypes
                    }
                }

                private val allowedCardTypes = arrayOf(
                    CardBrand.AmericanExpress.code,
                    CardBrand.DinersClub.code,
                    CardBrand.Discover.code,
                    CardBrand.JCB.code,
                    CardBrand.MasterCard.code,
                    CardBrand.UnionPay.code,
                    CardBrand.Visa.code
                )

                private val usdCardTypes = allowedCardTypes
                private val nonUsdCardTypes = arrayOf(
                    CardBrand.AmericanExpress.code,
                    CardBrand.MasterCard.code,
                    CardBrand.Visa.code
                )
            }
        }
    }
}

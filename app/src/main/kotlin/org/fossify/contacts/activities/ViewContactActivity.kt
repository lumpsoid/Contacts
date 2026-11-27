package org.fossify.contacts.activities

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.dialogs.CallConfirmationDialog
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.SelectAlarmSoundDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.contacts.*
import org.fossify.contacts.R
import org.fossify.contacts.databinding.*
import org.fossify.contacts.dialogs.ChooseSocialDialog
import org.fossify.contacts.dialogs.ManageVisibleFieldsDialog
import org.fossify.contacts.extensions.*
import org.fossify.contacts.helpers.*
import org.fossify.contacts.models.TransferMode
import org.fossify.contacts.screens.viewcontact.IntentData
import org.fossify.contacts.screens.viewcontact.IntentFacts
import org.fossify.contacts.screens.viewcontact.ViewContactAction
import org.fossify.contacts.screens.viewcontact.ViewContactEffect
import org.fossify.contacts.screens.viewcontact.ViewContactNotice
import org.fossify.contacts.screens.viewcontact.ViewContactState
import org.fossify.contacts.screens.viewcontact.update
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ViewContactActivity : ContactActivity() {
    private var duplicateContacts = ArrayList<Contact>()
    private var contactSources = ArrayList<ContactSource>()
    private var showFields = 0
    private var duplicateInitialized = false
    private var state = ViewContactState()
    // One worker and a FIFO queue keep effects in order; the idle thread times out, so the executor is never shut
    // down and a chain still in flight (delete, then delete the linked raws) completes after onDestroy.
    private val effectRunner = ThreadPoolExecutor(
        1, 1, EFFECT_THREAD_IDLE_SECONDS, TimeUnit.SECONDS, LinkedBlockingQueue()
    ).apply { allowCoreThreadTimeOut(true) }
    private val mergeDuplicate: Boolean get() = config.mergeDuplicateContacts
    private val binding by viewBinding(ActivityViewContactBinding::inflate)

    companion object {
        private const val COMPARABLE_PHONE_NUMBER_LENGTH = 9
        private const val EFFECT_THREAD_IDLE_SECONDS = 30L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (checkAppSideloading()) {
            return
        }

        setupEdgeToEdge(padBottomSystem = listOf(binding.contactScrollview))

        showFields = config.showContactFields
        setupMenu()
    }

    override fun onResume() {
        super.onResume()
        dispatch(ViewContactAction.Resumed(intentFacts()))
    }

    private fun intentFacts(): IntentFacts {
        val contactId = try {
            intent.getIntExtra(CONTACT_ID, 0)
        } catch (e: Exception) {
            null
        }
        val data = intent.data
        return IntentFacts(
            contactId = contactId,
            isPrivate = intent.getBooleanExtra(IS_PRIVATE, false),
            isViewIntent = intent.action == ContactsContract.QuickContact.ACTION_QUICK_CONTACT ||
                intent.action == Intent.ACTION_VIEW,
            data = when {
                data == null -> IntentData.NONE
                data.path?.contains("lookup") == true -> IntentData.LOOKUP
                else -> IntentData.CONTACT
            }
        )
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.contactPhotoBig.alpha == 1f) {
            hideBigContactPhoto()
            true
        } else {
            false
        }
    }

    private fun setupMenu() {
        binding.contactToolbar.menu.apply {
            findItem(R.id.share).setOnMenuItemClickListener {
                dispatch(ViewContactAction.ShareClicked)
                true
            }

            findItem(R.id.edit).setOnMenuItemClickListener {
                dispatch(ViewContactAction.EditClicked(config.mergeDuplicateContacts))
                true
            }

            findItem(R.id.open_with).setOnMenuItemClickListener {
                dispatch(ViewContactAction.OpenWithClicked)
                true
            }

            findItem(R.id.delete).setOnMenuItemClickListener {
                dispatch(ViewContactAction.DeleteClicked(binding.contactSourcesHolder.childCount, mergeDuplicate))
                true
            }

            findItem(R.id.unlink).setOnMenuItemClickListener {
                dispatch(ViewContactAction.UnlinkClicked)
                true
            }

            findItem(R.id.move_to_account).setOnMenuItemClickListener {
                dispatch(ViewContactAction.TransferClicked(TransferMode.MOVE))
                true
            }

            findItem(R.id.copy_to_account).setOnMenuItemClickListener {
                dispatch(ViewContactAction.TransferClicked(TransferMode.COPY))
                true
            }

            findItem(R.id.manage_visible_fields).setOnMenuItemClickListener {
                ManageVisibleFieldsDialog(this@ViewContactActivity) {
                    showFields = config.showContactFields
                    dispatch(ViewContactAction.VisibleFieldsChanged)
                }
                true
            }
        }

        binding.contactToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun gotContact() {
        if (isDestroyed || isFinishing) {
            return
        }

        binding.contactScrollview.beVisible()
        setupViewContact()
        binding.contactSendSms.beVisibleIf(contact!!.phoneNumbers.isNotEmpty())
        binding.contactStartCall.beVisibleIf(contact!!.phoneNumbers.isNotEmpty())
        binding.contactSendEmail.beVisibleIf(contact!!.emails.isNotEmpty())

        if (contact!!.photoUri.isEmpty() && contact!!.photo == null) {
            showPhotoPlaceholder(binding.contactPhoto)
            binding.contactPhotoBottomShadow.beGone()
        } else {
            updateContactPhoto(contact!!.photoUri, binding.contactPhoto, binding.contactPhotoBottomShadow, contact!!.photo)
            val options = RequestOptions()
                .transform(FitCenter(), RoundedCorners(resources.getDimension(org.fossify.commons.R.dimen.normal_margin).toInt()))

            Glide.with(this)
                .load(contact!!.photo ?: currentContactPhotoPath)
                .apply(options)
                .into(binding.contactPhotoBig)

            binding.contactPhoto.setOnClickListener {
                binding.contactPhotoBig.alpha = 0f
                binding.contactPhotoBig.beVisible()
                binding.contactPhotoBig.animate().alpha(1f).start()
            }

            binding.contactPhotoBig.setOnClickListener {
                hideBigContactPhoto()
            }
        }

        val textColor = getProperTextColor()
        arrayOf(
            binding.contactNameImage, binding.contactNumbersImage, binding.contactEmailsImage, binding.contactAddressesImage, binding.contactImsImage,
            binding.contactEventsImage, binding.contactSourceImage, binding.contactNotesImage, binding.contactRingtoneImage, binding.contactOrganizationImage,
            binding.contactWebsitesImage, binding.contactGroupsImage
        ).forEach {
            it.applyColorFilter(textColor)
        }

        binding.contactSendSms.setOnClickListener { trySendSMS() }
        binding.contactStartCall.setOnClickListener { tryInitiateCall(contact!!) { startCallIntent(it) } }
        binding.contactSendEmail.setOnClickListener { trySendEmail() }

        binding.contactSendSms.setOnLongClickListener { toast(org.fossify.commons.R.string.send_sms); true; }
        binding.contactStartCall.setOnLongClickListener { toast(org.fossify.commons.R.string.call_contact); true; }
        binding.contactSendEmail.setOnLongClickListener { toast(org.fossify.commons.R.string.send_email); true; }

        updateTextColors(binding.contactScrollview)
    }

    private fun setupViewContact() {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setupFavorite()
        setupNames()

        ContactsHelper(this).getContactSources {
            contactSources = it
            runOnUiThread {
                setupContactDetails()
            }
        }

        initializeDuplicateContacts {
            duplicateInitialized = true
            setupContactDetails()
        }
    }

    private fun setupContactDetails() {
        if (isFinishing || isDestroyed || contact == null) {
            return
        }

        setupPhoneNumbers()
        setupEmails()
        setupAddresses()
        setupIMs()
        setupEvents()
        setupWebsites()
        setupGroups()
        setupContactSources()
        setupNotes()
        setupRingtone()
        setupOrganization()
        updateTextColors(binding.contactScrollview)
    }

    private fun dispatch(action: ViewContactAction) {
        val next = update(state, action)
        state = next.state
        contact = state.contact
        renderMenu()
        if (next.effects.isNotEmpty()) {
            effectRunner.execute { next.effects.forEach { runEffect(it) } }
        }
    }

    private fun post(action: ViewContactAction) = runOnUiThread { dispatch(action) }

    private fun renderMenu() {
        binding.contactToolbar.menu.apply {
            findItem(R.id.open_with).isVisible = state.showOpenWith
            findItem(R.id.unlink).isVisible = state.showUnlink
        }
    }

    private fun runEffect(effect: ViewContactEffect) {
        when (effect) {
            is ViewContactEffect.Ui -> runOnUiThread { runUi(effect) }
            is ViewContactEffect.Load -> runLoad(effect)
            is ViewContactEffect.Write -> runWrite(effect)
        }
    }

    private fun runUi(effect: ViewContactEffect.Ui) {
        when (effect) {
            ViewContactEffect.RequestReadPermission ->
                handlePermission(PERMISSION_READ_CONTACTS) { post(ViewContactAction.PermissionResult(it)) }

            ViewContactEffect.ShowContact -> gotContact()
            is ViewContactEffect.Notify -> toast(noticeText(effect.notice))
            ViewContactEffect.Finish -> finish()
            is ViewContactEffect.ConfirmDelete -> confirmDelete(effect.fromAllSources)
            is ViewContactEffect.Share -> shareContact(effect.contact)
            is ViewContactEffect.Edit -> {
                duplicateInitialized = false
                editContact(effect.contact, effect.isMergedDuplicate)
            }

            is ViewContactEffect.EditExact -> {
                duplicateInitialized = false
                editContact(effect.contact)
            }

            is ViewContactEffect.OpenWith -> launchViewContactIntent(getContactPublicUri(effect.contact))
            is ViewContactEffect.Transfer ->
                transferContacts(listOf(effect.contact), effect.mode) { done ->
                    post(ViewContactAction.Transferred(effect.mode, done))
                }
        }
    }

    private fun noticeText(notice: ViewContactNotice): Int = when (notice) {
        ViewContactNotice.UNKNOWN_ERROR -> org.fossify.commons.R.string.unknown_error_occurred
        ViewContactNotice.NO_CONTACTS_PERMISSION -> org.fossify.commons.R.string.no_contacts_permission
        ViewContactNotice.CONTACTS_UNLINKED -> R.string.contacts_unlinked
    }

    private fun runLoad(effect: ViewContactEffect.Load) {
        when (effect) {
            ViewContactEffect.ResolveLookupUri -> resolveLookupUri()
            ViewContactEffect.ResolveContactUri ->
                intent.data?.let { post(ViewContactAction.UriResolved(getContactUriRawId(it))) }

            is ViewContactEffect.LoadContact ->
                post(ViewContactAction.ContactLoaded(getContactWithIdOrRaw(effect.id, effect.isPrivate)))

            is ViewContactEffect.LoadLinkedRawIds -> {
                val ids = ContactLinkHelper(this).getLinkedRawContactIds(effect.contactId)
                post(ViewContactAction.LinkedRawIdsLoaded(ids))
            }

            is ViewContactEffect.LoadMergedDeleteScope -> {
                // read the links first: they are gone once the contact is deleted
                val linked = ContactLinkHelper(this).getLinkedRawContactIdsOf(listOf(effect.contact))
                getDuplicatesKeptTogether(effect.contact, true) {
                    post(ViewContactAction.MergedDeleteScopeLoaded(it, linked))
                }
            }
        }
    }

    private fun resolveLookupUri() {
        val data = intent.data ?: return
        val lookupKey = getLookupKeyFromUri(data)
        val rawId = getLookupUriRawId(data)
        if (lookupKey == null) {
            post(ViewContactAction.UriResolved(rawId))
        } else {
            post(ViewContactAction.LookupResolved(ContactsHelper(this).getContactWithLookupKey(lookupKey), rawId))
        }
    }

    private fun runWrite(effect: ViewContactEffect.Write) {
        when (effect) {
            is ViewContactEffect.DeleteMerged -> {
                val ok = ContactsHelper(this).deleteContacts(ArrayList(effect.contacts))
                post(ViewContactAction.MergedDeleted(ok, effect.linkedRawIds))
            }

            is ViewContactEffect.DeleteSingle ->
                ContactsHelper(this).deleteContact(effect.contact, false) { post(ViewContactAction.SingleDeleted) }

            is ViewContactEffect.DeleteRawContacts -> {
                ContactLinkHelper(this).deleteRawContacts(effect.rawIds)
            }

            is ViewContactEffect.Unlink ->
                post(ViewContactAction.Unlinked(ContactLinkHelper(this).unlinkRawContacts(effect.rawIds)))

            is ViewContactEffect.AddFavorite -> ContactsHelper(this).addFavorites(arrayListOf(effect.contact))
            is ViewContactEffect.RemoveFavorite -> ContactsHelper(this).removeFavorites(arrayListOf(effect.contact))
            is ViewContactEffect.SaveLocalRingtone ->
                LocalContactsHelper(this).updateRingtone(effect.contactId, effect.path)

            is ViewContactEffect.SaveProviderRingtone ->
                ContactsHelper(this).updateRingtone(effect.contactId.toString(), effect.path)
        }
    }

    private fun setupFavorite() {
        binding.contactToggleFavorite.apply {
            beVisible()
            tag = contact!!.starred
            setImageDrawable(getStarDrawable(tag == 1))

            setOnClickListener {
                dispatch(ViewContactAction.FavoriteToggled)
                tag = contact!!.starred
                setImageDrawable(getStarDrawable(tag == 1))
            }

            setOnLongClickListener { toast(R.string.toggle_favorite); true; }
        }
    }

    private fun setupNames() {
        var displayName = contact!!.getNameToDisplay()
        if (contact!!.nickname.isNotEmpty()) {
            displayName += " (${contact!!.nickname})"
        }

        val showNameFields = showFields and SHOW_PREFIX_FIELD != 0 || showFields and SHOW_FIRST_NAME_FIELD != 0 || showFields and SHOW_MIDDLE_NAME_FIELD != 0 ||
            showFields and SHOW_SURNAME_FIELD != 0 || showFields and SHOW_SUFFIX_FIELD != 0

        binding.contactName.text = displayName
        binding.contactName.copyOnLongClick(displayName)
        binding.contactName.beVisibleIf(displayName.isNotEmpty() && !contact!!.isABusinessContact() && showNameFields)
        binding.contactNameImage.beInvisibleIf(binding.contactName.isGone())
    }

    private fun setupPhoneNumbers() {
        var phoneNumbers = contact!!.phoneNumbers.toMutableSet() as LinkedHashSet<PhoneNumber>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                phoneNumbers.addAll(it.phoneNumbers)
            }
        }

        if (duplicateInitialized) {
            val contactDefaultsNumbers = contact!!.phoneNumbers.filter { it.isPrimary }
            val duplicateContactsDefaultNumbers = duplicateContacts.flatMap { it.phoneNumbers }.filter { it.isPrimary }
            val defaultNumbers = (contactDefaultsNumbers + duplicateContactsDefaultNumbers).toSet()

            if (defaultNumbers.size > 1 && defaultNumbers.distinctBy { it.normalizedNumber }.size > 1) {
                phoneNumbers.forEach { it.isPrimary = false }
            } else if (defaultNumbers.size == 1) {
                if (mergeDuplicate) {
                    val defaultNumber = defaultNumbers.first()
                    val candidate = phoneNumbers.find { it.normalizedNumber == defaultNumber.normalizedNumber && !it.isPrimary }
                    candidate?.isPrimary = true
                } else {
                    duplicateContactsDefaultNumbers.forEach { defaultNumber ->
                        val candidate = phoneNumbers.find { it.normalizedNumber == defaultNumber.normalizedNumber && !it.isPrimary }
                        candidate?.isPrimary = true
                    }
                }
            }
        }

        phoneNumbers = phoneNumbers.distinctBy {
            if (it.normalizedNumber.length >= COMPARABLE_PHONE_NUMBER_LENGTH) {
                it.normalizedNumber.substring(it.normalizedNumber.length - COMPARABLE_PHONE_NUMBER_LENGTH)
            } else {
                it.normalizedNumber
            }
        }.toMutableSet() as LinkedHashSet<PhoneNumber>

        phoneNumbers = phoneNumbers.sortedBy { it.type }.toMutableSet() as LinkedHashSet<PhoneNumber>
        contact!!.phoneNumbers = phoneNumbers.toMutableList() as ArrayList<PhoneNumber>
        binding.contactNumbersHolder.removeAllViews()

        if (phoneNumbers.isNotEmpty() && showFields and SHOW_PHONE_NUMBERS_FIELD != 0) {
            phoneNumbers.forEach { phoneNumber ->
                ItemViewPhoneNumberBinding.inflate(layoutInflater, binding.contactNumbersHolder, false).apply {
                    binding.contactNumbersHolder.addView(root)
                    if (config.formatPhoneNumbers) {
                        contactNumber.text = phoneNumber.value.formatPhoneNumber()
                    } else {
                        contactNumber.text = phoneNumber.value
                    }
                    contactNumberType.text = getPhoneNumberTypeText(phoneNumber.type, phoneNumber.label)
                    root.copyOnLongClick(phoneNumber.value)

                    root.setOnClickListener {
                        if (config.showCallConfirmation) {
                            CallConfirmationDialog(this@ViewContactActivity, phoneNumber.value) {
                                startCallIntent(phoneNumber.value)
                            }
                        } else {
                            startCallIntent(phoneNumber.value)
                        }
                    }

                    defaultToggleIcon.isVisible = phoneNumber.isPrimary
                    defaultToggleIcon.applyColorFilter(getProperTextColor())
                }
            }
            binding.contactNumbersImage.beVisible()
            binding.contactNumbersHolder.beVisible()
        } else {
            binding.contactNumbersImage.beGone()
            binding.contactNumbersHolder.beGone()
        }

        // make sure the Call and SMS buttons are visible if any phone number is shown
        if (phoneNumbers.isNotEmpty()) {
            binding.contactSendSms.beVisible()
            binding.contactStartCall.beVisible()
        }
    }

    // a contact cannot have different emails per contact source. Such contacts are handled as separate ones, not duplicates of each other
    private fun setupEmails() {
        binding.contactEmailsHolder.removeAllViews()
        val emails = contact!!.emails
        if (emails.isNotEmpty() && showFields and SHOW_EMAILS_FIELD != 0) {
            emails.forEach {
                ItemViewEmailBinding.inflate(layoutInflater, binding.contactEmailsHolder, false).apply {
                    val email = it
                    binding.contactEmailsHolder.addView(root)
                    contactEmail.text = email.value
                    contactEmailType.text = getEmailTypeText(email.type, email.label)
                    root.copyOnLongClick(email.value)

                    root.setOnClickListener {
                        sendEmailIntent(email.value)
                    }
                }
            }
            binding.contactEmailsImage.beVisible()
            binding.contactEmailsHolder.beVisible()
        } else {
            binding.contactEmailsImage.beGone()
            binding.contactEmailsHolder.beGone()
        }
    }

    private fun setupAddresses() {
        var addresses = contact!!.addresses.toMutableSet() as LinkedHashSet<Address>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                addresses.addAll(it.addresses)
            }
        }

        addresses = addresses.sortedBy { it.type }.toMutableSet() as LinkedHashSet<Address>
        contact!!.addresses = addresses.toMutableList() as ArrayList<Address>
        binding.contactAddressesHolder.removeAllViews()

        if (addresses.isNotEmpty() && showFields and SHOW_ADDRESSES_FIELD != 0) {
            addresses.forEach {
                ItemViewAddressBinding.inflate(layoutInflater, binding.contactAddressesHolder, false).apply {
                    val address = it
                    binding.contactAddressesHolder.addView(root)
                    contactAddress.text = address.value
                    contactAddressType.text = getAddressTypeText(address.type, address.label)
                    root.copyOnLongClick(address.value)

                    root.setOnClickListener {
                        sendAddressIntent(address.value)
                    }
                }
            }
            binding.contactAddressesImage.beVisible()
            binding.contactAddressesHolder.beVisible()
        } else {
            binding.contactAddressesImage.beGone()
            binding.contactAddressesHolder.beGone()
        }
    }

    private fun setupIMs() {
        var IMs = contact!!.IMs.toMutableSet() as LinkedHashSet<IM>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                IMs.addAll(it.IMs)
            }
        }

        IMs = IMs.sortedBy { it.type }.toMutableSet() as LinkedHashSet<IM>
        contact!!.IMs = IMs.toMutableList() as ArrayList<IM>
        binding.contactImsHolder.removeAllViews()

        if (IMs.isNotEmpty() && showFields and SHOW_IMS_FIELD != 0) {
            IMs.forEach {
                ItemViewImBinding.inflate(layoutInflater, binding.contactImsHolder, false).apply {
                    val IM = it
                    binding.contactImsHolder.addView(root)
                    contactIm.text = IM.value
                    contactImType.text = getIMTypeText(IM.type, IM.label)
                    root.copyOnLongClick(IM.value)
                }
            }
            binding.contactImsImage.beVisible()
            binding.contactImsHolder.beVisible()
        } else {
            binding.contactImsImage.beGone()
            binding.contactImsHolder.beGone()
        }
    }

    private fun setupEvents() {
        var events = contact!!.events.toMutableSet() as LinkedHashSet<Event>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                events.addAll(it.events)
            }
        }

        events = events.sortedBy { it.type }.toMutableSet() as LinkedHashSet<Event>
        contact!!.events = events.toMutableList() as ArrayList<Event>
        binding.contactEventsHolder.removeAllViews()

        if (events.isNotEmpty() && showFields and SHOW_EVENTS_FIELD != 0) {
            events.forEach {
                ItemViewEventBinding.inflate(layoutInflater, binding.contactEventsHolder, false).apply {
                    binding.contactEventsHolder.addView(root)
                    it.value.getDateTimeFromDateString(true, contactEvent)
                    contactEventType.setText(getEventTextId(it.type))
                    root.copyOnLongClick(it.value)
                }
            }
            binding.contactEventsImage.beVisible()
            binding.contactEventsHolder.beVisible()
        } else {
            binding.contactEventsImage.beGone()
            binding.contactEventsHolder.beGone()
        }
    }

    private fun setupWebsites() {
        var websites = contact!!.websites.toMutableSet() as LinkedHashSet<String>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                websites.addAll(it.websites)
            }
        }

        websites = websites.sorted().toMutableSet() as LinkedHashSet<String>
        contact!!.websites = websites.toMutableList() as ArrayList<String>
        binding.contactWebsitesHolder.removeAllViews()

        if (websites.isNotEmpty() && showFields and SHOW_WEBSITES_FIELD != 0) {
            websites.forEach {
                val url = it
                ItemWebsiteBinding.inflate(layoutInflater, binding.contactWebsitesHolder, false).apply {
                    binding.contactWebsitesHolder.addView(root)
                    contactWebsite.text = url
                    root.copyOnLongClick(url)

                    root.setOnClickListener {
                        openWebsiteIntent(url)
                    }
                }
            }
            binding.contactWebsitesImage.beVisible()
            binding.contactWebsitesHolder.beVisible()
        } else {
            binding.contactWebsitesImage.beGone()
            binding.contactWebsitesHolder.beGone()
        }
    }

    private fun setupGroups() {
        var groups = contact!!.groups.toMutableSet() as LinkedHashSet<Group>

        if (mergeDuplicate) {
            duplicateContacts.forEach {
                groups.addAll(it.groups)
            }
        }

        groups = groups.sortedBy { it.title }.toMutableSet() as LinkedHashSet<Group>
        contact!!.groups = groups.toMutableList() as ArrayList<Group>
        binding.contactGroupsHolder.removeAllViews()

        if (groups.isNotEmpty() && showFields and SHOW_GROUPS_FIELD != 0) {
            groups.forEach {
                ItemViewGroupBinding.inflate(layoutInflater, binding.contactGroupsHolder, false).apply {
                    val group = it
                    binding.contactGroupsHolder.addView(root)
                    contactGroup.text = group.title
                    root.copyOnLongClick(group.title)
                }
            }
            binding.contactGroupsImage.beVisible()
            binding.contactGroupsHolder.beVisible()
        } else {
            binding.contactGroupsImage.beGone()
            binding.contactGroupsHolder.beGone()
        }
    }

    private fun setupContactSources() {
        binding.contactSourcesHolder.removeAllViews()
        if (showFields and SHOW_CONTACT_SOURCE_FIELD != 0) {
            var sources = HashMap<Contact, String>()
            sources[contact!!] = getPublicContactSourceSync(contact!!.source, contactSources)

            if (mergeDuplicate) {
                duplicateContacts.forEach {
                    sources[it] = getPublicContactSourceSync(it.source, contactSources)
                }
            }

            if (sources.size > 1) {
                sources = sources.toList().sortedBy { (key, value) -> value.lowercase(Locale.getDefault()) }.toMap() as LinkedHashMap<Contact, String>
            }

            for ((key, value) in sources) {
                ItemViewContactSourceBinding.inflate(layoutInflater, binding.contactSourcesHolder, false).apply {
                    contactSource.text = if (value == "") getString(R.string.phone_storage) else value
                    contactSource.copyOnLongClick(value)
                    binding.contactSourcesHolder.addView(root)

                    contactSource.setOnClickListener {
                        dispatch(ViewContactAction.EditSourceClicked(key))
                    }

                    if (value.lowercase(Locale.getDefault()) == WHATSAPP) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(WHATSAPP_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == SIGNAL) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(SIGNAL_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == VIBER) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(VIBER_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == TELEGRAM) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(TELEGRAM_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }

                    if (value.lowercase(Locale.getDefault()) == THREEMA) {
                        contactSourceImage.setImageDrawable(getPackageDrawable(THREEMA_PACKAGE))
                        contactSourceImage.beVisible()
                        contactSourceImage.setOnClickListener {
                            showSocialActions(key.id)
                        }
                    }
                }
            }

            binding.contactSourceImage.beVisible()
            binding.contactSourcesHolder.beVisible()
        } else {
            binding.contactSourceImage.beGone()
            binding.contactSourcesHolder.beGone()
        }
    }

    private fun setupNotes() {
        val notes = contact!!.notes
        if (notes.isNotEmpty() && showFields and SHOW_NOTES_FIELD != 0) {
            binding.contactNotes.text = notes
            binding.contactNotesImage.beVisible()
            binding.contactNotes.beVisible()
            binding.contactNotes.copyOnLongClick(notes)
        } else {
            binding.contactNotesImage.beGone()
            binding.contactNotes.beGone()
        }
    }

    private fun setupRingtone() {
        if (showFields and SHOW_RINGTONE_FIELD != 0) {
            binding.contactRingtoneImage.beVisible()
            binding.contactRingtone.beVisible()

            val ringtone = contact!!.ringtone
            if (ringtone?.isEmpty() == true) {
                binding.contactRingtone.text = getString(org.fossify.commons.R.string.no_sound)
            } else if (ringtone?.isNotEmpty() == true && ringtone != getDefaultRingtoneUri().toString()) {
                if (ringtone == SILENT) {
                    binding.contactRingtone.text = getString(org.fossify.commons.R.string.no_sound)
                } else {
                    systemRingtoneSelected(Uri.parse(ringtone))
                }
            } else {
                binding.contactRingtoneImage.beGone()
                binding.contactRingtone.beGone()
                return
            }

            binding.contactRingtone.copyOnLongClick(binding.contactRingtone.text.toString())

            binding.contactRingtone.setOnClickListener {
                val ringtonePickerIntent = getRingtonePickerIntent()
                try {
                    startActivityForResult(ringtonePickerIntent, INTENT_SELECT_RINGTONE)
                } catch (e: Exception) {
                    val currentRingtone = contact!!.ringtone ?: getDefaultAlarmSound(RingtoneManager.TYPE_RINGTONE).uri
                    SelectAlarmSoundDialog(this@ViewContactActivity,
                        currentRingtone,
                        AudioManager.STREAM_RING,
                        PICK_RINGTONE_INTENT_ID,
                        RingtoneManager.TYPE_RINGTONE,
                        true,
                        onAlarmPicked = {
                            binding.contactRingtone.text = it?.title
                            ringtoneUpdated(it?.uri)
                        },
                        onAlarmSoundDeleted = {}
                    )
                }
            }
        } else {
            binding.contactRingtoneImage.beGone()
            binding.contactRingtone.beGone()
        }
    }

    private fun setupOrganization() {
        val organization = contact!!.organization
        if (organization.isNotEmpty() && showFields and SHOW_ORGANIZATION_FIELD != 0) {
            binding.contactOrganizationCompany.text = organization.company
            binding.contactOrganizationJobPosition.text = organization.jobPosition
            binding.contactOrganizationImage.beGoneIf(organization.isEmpty())
            binding.contactOrganizationCompany.beGoneIf(organization.company.isEmpty())
            binding.contactOrganizationJobPosition.beGoneIf(organization.jobPosition.isEmpty())
            binding.contactOrganizationCompany.copyOnLongClick(binding.contactOrganizationCompany.value)
            binding.contactOrganizationJobPosition.copyOnLongClick(binding.contactOrganizationJobPosition.value)

            if (organization.company.isEmpty() && organization.jobPosition.isNotEmpty()) {
                (binding.contactOrganizationImage.layoutParams as RelativeLayout.LayoutParams).addRule(
                    RelativeLayout.ALIGN_TOP,
                    binding.contactOrganizationJobPosition.id
                )
            }
        } else {
            binding.contactOrganizationImage.beGone()
            binding.contactOrganizationCompany.beGone()
            binding.contactOrganizationJobPosition.beGone()
        }
    }

    private fun showSocialActions(contactId: Int) {
        ensureBackgroundThread {
            val actions = getSocialActions(contactId)
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    ChooseSocialDialog(this@ViewContactActivity, actions) { action ->
                        Intent(Intent.ACTION_VIEW).apply {
                            val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
                            setDataAndType(uri, action.mimetype)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                            try {
                                startActivity(this)
                            } catch (e: SecurityException) {
                                handlePermission(PERMISSION_CALL_PHONE) { success ->
                                    if (success) {
                                        startActivity(this)
                                    } else {
                                        toast(org.fossify.commons.R.string.no_phone_call_permission)
                                    }
                                }
                            } catch (e: ActivityNotFoundException) {
                                toast(org.fossify.commons.R.string.no_app_found)
                            } catch (e: Exception) {
                                showErrorToast(e)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun customRingtoneSelected(ringtonePath: String) {
        binding.contactRingtone.text = ringtonePath.getFilenameFromPath()
        ringtoneUpdated(ringtonePath)
    }

    override fun systemRingtoneSelected(uri: Uri?) {
        val contactRingtone = RingtoneManager.getRingtone(this, uri)
        binding.contactRingtone.text = contactRingtone.getTitle(this)
        ringtoneUpdated(uri?.toString() ?: "")
    }

    private fun ringtoneUpdated(path: String?) {
        dispatch(ViewContactAction.RingtonePicked(path))
    }

    private fun initializeDuplicateContacts(callback: () -> Unit) {
        getDuplicateContacts(contact!!, false) {
            duplicateContacts = it
            runOnUiThread {
                callback()
            }
        }
    }

    private fun confirmDelete(fromAllSources: Boolean) {
        val addition = if (fromAllSources) "\n\n${getString(R.string.delete_from_all_sources)}" else ""
        val message = "${getString(org.fossify.commons.R.string.proceed_with_deletion)}$addition"
        ConfirmationDialog(this, message) {
            dispatch(ViewContactAction.DeleteConfirmed(mergeDuplicate))
        }
    }

    private fun getStarDrawable(on: Boolean) =
        resources.getDrawable(if (on) org.fossify.commons.R.drawable.ic_star_vector else org.fossify.commons.R.drawable.ic_star_outline_vector)

    private fun hideBigContactPhoto() {
        binding.contactPhotoBig.animate().alpha(0f).withEndAction { binding.contactPhotoBig.beGone() }.start()
    }

    private fun View.copyOnLongClick(value: String) {
        setOnLongClickListener {
            copyToClipboard(value)
            true
        }
    }
}

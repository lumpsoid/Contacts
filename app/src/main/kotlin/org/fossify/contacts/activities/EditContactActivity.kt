package org.fossify.contacts.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.MediaStore
import android.telephony.PhoneNumberUtils
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import org.fossify.commons.dialogs.ConfirmationAdvancedDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.dialogs.SelectAlarmSoundDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beInvisible
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkAppSideloading
import org.fossify.commons.extensions.getCachePhoto
import org.fossify.commons.extensions.getContactPublicUri
import org.fossify.commons.extensions.getContactUriRawId
import org.fossify.commons.extensions.getDateTimeFromDateString
import org.fossify.commons.extensions.getDefaultAlarmSound
import org.fossify.commons.extensions.getEmptyContact
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getLookupUriRawId
import org.fossify.commons.extensions.getPhoneNumberTypeText
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getPublicContactSource
import org.fossify.commons.extensions.getVisibleContactSources
import org.fossify.commons.extensions.hasContactPermissions
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toInt
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.CONTACT_ID
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.DEFAULT_ADDRESS_TYPE
import org.fossify.commons.helpers.DEFAULT_EMAIL_TYPE
import org.fossify.commons.helpers.DEFAULT_EVENT_TYPE
import org.fossify.commons.helpers.DEFAULT_IM_TYPE
import org.fossify.commons.helpers.DEFAULT_PHONE_NUMBER_TYPE
import org.fossify.commons.helpers.IS_PRIVATE
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.PERMISSION_WRITE_CONTACTS
import org.fossify.commons.helpers.PHOTO_ADDED
import org.fossify.commons.helpers.PHOTO_CHANGED
import org.fossify.commons.helpers.PHOTO_REMOVED
import org.fossify.commons.helpers.PHOTO_UNCHANGED
import org.fossify.commons.helpers.SHOW_ADDRESSES_FIELD
import org.fossify.commons.helpers.SHOW_CONTACT_SOURCE_FIELD
import org.fossify.commons.helpers.SHOW_EMAILS_FIELD
import org.fossify.commons.helpers.SHOW_EVENTS_FIELD
import org.fossify.commons.helpers.SHOW_FIRST_NAME_FIELD
import org.fossify.commons.helpers.SHOW_GROUPS_FIELD
import org.fossify.commons.helpers.SHOW_IMS_FIELD
import org.fossify.commons.helpers.SHOW_MIDDLE_NAME_FIELD
import org.fossify.commons.helpers.SHOW_NICKNAME_FIELD
import org.fossify.commons.helpers.SHOW_NOTES_FIELD
import org.fossify.commons.helpers.SHOW_ORGANIZATION_FIELD
import org.fossify.commons.helpers.SHOW_PHONE_NUMBERS_FIELD
import org.fossify.commons.helpers.SHOW_PREFIX_FIELD
import org.fossify.commons.helpers.SHOW_RINGTONE_FIELD
import org.fossify.commons.helpers.SHOW_STRUCTURED_ADDRESSES_FIELD
import org.fossify.commons.helpers.SHOW_SUFFIX_FIELD
import org.fossify.commons.helpers.SHOW_SURNAME_FIELD
import org.fossify.commons.helpers.SHOW_WEBSITES_FIELD
import org.fossify.commons.helpers.SILENT
import org.fossify.commons.helpers.SMT_PRIVATE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import org.fossify.commons.models.contacts.Group
import org.fossify.commons.views.MyAutoCompleteTextView
import org.fossify.contacts.R
import org.fossify.contacts.adapters.AutoCompleteTextViewAdapter
import org.fossify.contacts.databinding.ActivityEditContactBinding
import org.fossify.contacts.databinding.ItemEditAddressBinding
import org.fossify.contacts.databinding.ItemEditEmailBinding
import org.fossify.contacts.databinding.ItemEditGroupBinding
import org.fossify.contacts.databinding.ItemEditImBinding
import org.fossify.contacts.databinding.ItemEditPhoneNumberBinding
import org.fossify.contacts.databinding.ItemEditStructuredAddressBinding
import org.fossify.contacts.databinding.ItemEditWebsiteBinding
import org.fossify.contacts.databinding.ItemEventBinding
import org.fossify.contacts.dialogs.CustomLabelDialog
import org.fossify.contacts.dialogs.ManageVisibleFieldsDialog
import org.fossify.contacts.dialogs.MyDatePickerDialog
import org.fossify.contacts.dialogs.SelectGroupsDialog
import org.fossify.contacts.extensions.config
import org.fossify.contacts.extensions.getCachePhotoUri
import org.fossify.contacts.extensions.getContactWithIdOrRaw
import org.fossify.contacts.extensions.getDuplicatesKeptTogether
import org.fossify.contacts.extensions.showContactSourcePicker
import org.fossify.contacts.helpers.ADDRESS_TYPE_LABELS
import org.fossify.contacts.helpers.ADD_NEW_CONTACT_NUMBER
import org.fossify.contacts.helpers.AddressRow
import org.fossify.contacts.helpers.EMAIL_TYPE_LABELS
import org.fossify.contacts.helpers.EVENT_TYPE_LABELS
import org.fossify.contacts.helpers.EventRow
import org.fossify.contacts.helpers.FormSnapshot
import org.fossify.contacts.helpers.IM_TYPE_LABELS
import org.fossify.contacts.helpers.IS_FROM_SIMPLE_CONTACTS
import org.fossify.contacts.helpers.KEY_EMAIL
import org.fossify.contacts.helpers.KEY_NAME
import org.fossify.contacts.helpers.NumberRow
import org.fossify.contacts.helpers.PHONE_TYPE_LABELS
import org.fossify.contacts.helpers.PlainAddress
import org.fossify.contacts.helpers.Prefill
import org.fossify.contacts.helpers.RawContactLoader
import org.fossify.contacts.helpers.StructuredAddress
import org.fossify.contacts.helpers.TypeLabels
import org.fossify.contacts.helpers.TypedRow
import org.fossify.contacts.helpers.applyTo
import org.fossify.contacts.helpers.streetForStructuredEdit
import org.fossify.contacts.helpers.typeIdOf
import org.fossify.contacts.screens.editcontact.BackFacts
import org.fossify.contacts.screens.editcontact.EditContactAction
import org.fossify.contacts.screens.editcontact.EditContactEffect
import org.fossify.contacts.screens.editcontact.EditContactNotice
import org.fossify.contacts.screens.editcontact.EditContactState
import org.fossify.contacts.screens.editcontact.LeaveVia
import org.fossify.contacts.screens.editcontact.OpenFacts
import org.fossify.contacts.screens.editcontact.OpenKind
import org.fossify.contacts.screens.editcontact.OpenUri
import org.fossify.contacts.screens.editcontact.update
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class EditContactActivity : ContactActivity() {
    companion object {
        private const val INTENT_TAKE_PHOTO = 1
        private const val INTENT_CHOOSE_PHOTO = 2
        private const val INTENT_CROP_PHOTO = 3
        private const val LAST_PHOTO_INTENT_URI = "last_photo_intent_uri"

        private const val TAKE_PHOTO = 1
        private const val CHOOSE_PHOTO = 2
        private const val REMOVE_PHOTO = 3

        private const val AUTO_COMPLETE_DELAY = 5000L
        private const val EFFECT_THREAD_IDLE_SECONDS = 30L
    }

    private var editState = EditContactState()
    // One worker and a FIFO queue keep effects in order; the idle thread times out, so the executor is never shut
    // down and a chain still in flight (delete, then delete the linked raws) completes after onDestroy.
    private val effectRunner = ThreadPoolExecutor(
        1, 1, EFFECT_THREAD_IDLE_SECONDS, TimeUnit.SECONDS, LinkedBlockingQueue()
    ).apply { allowCoreThreadTimeOut(true) }
    private var wasActivityInitialized = false
    private var lastPhotoIntentUri: Uri? = null
    private var isSaving = false
    private var highlightLastPhoneNumber = false
    private var highlightLastEmail = false
    private var numberViewToColor: EditText? = null
    private var emailViewToColor: EditText? = null
    private var originalContactSource = ""
    private val binding by viewBinding(ActivityEditContactBinding::inflate)

    enum class PrimaryNumberStatus {
        UNCHANGED, STARRED, UNSTARRED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(LAST_PHOTO_INTENT_URI)?.let { lastPhotoIntentUri = Uri.parse(it) }
        setContentView(binding.root)

        if (checkAppSideloading()) {
            return
        }

        setupEdgeToEdge(
            padBottomImeAndSystem = listOf(binding.contactScrollview)
        )
        setupMenu()

        dispatch(EditContactAction.Opened(openFacts()))
    }

    /** The launch intent as plain values; each ContentValues of the "data" extra becomes a plain map. */
    private fun openFacts(): OpenFacts {
        val kind = when (intent.action) {
            Intent.ACTION_EDIT -> OpenKind.EDIT
            Intent.ACTION_INSERT -> OpenKind.INSERT
            ADD_NEW_CONTACT_NUMBER -> OpenKind.ADD_NUMBER
            else -> OpenKind.OTHER
        }
        val data = intent.data
        val path = data?.path
        return OpenFacts(
            kind = kind,
            fromSimpleContacts = intent.getBooleanExtra(IS_FROM_SIMPLE_CONTACTS, false),
            contactId = intent.getIntExtra(CONTACT_ID, 0),
            isPrivate = intent.getBooleanExtra(IS_PRIVATE, false),
            data = if (data != null && path != null) OpenUri(path, data.pathSegments.lastOrNull().orEmpty()) else null,
            prefill = intent.extras?.let { prefillOf(it) },
        )
    }

    private fun prefillOf(extras: Bundle) = Prefill(
        phoneExtra = intent.getStringExtra(KEY_PHONE),
        email = intent.getStringExtra(KEY_EMAIL),
        name = extras.get(KEY_NAME)?.toString(),
        rows = extras.getParcelableArrayList<ContentValues>("data").orEmpty()
            .map { values -> values.keySet().associateWith { values.get(it) } },
    )

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                INTENT_TAKE_PHOTO, INTENT_CHOOSE_PHOTO -> startCropPhotoIntent(lastPhotoIntentUri, resultData?.data)
                INTENT_CROP_PHOTO -> lastPhotoIntentUri?.let {
                    updateContactPhoto(it.toString(), binding.contactPhoto, binding.contactPhotoBottomShadow)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastPhotoIntentUri?.let { outState.putString(LAST_PHOTO_INTENT_URI, it.toString()) }
    }

    private fun gotContact(loaded: Contact?, prefill: Prefill?, addingNumber: Boolean) {
        contact = loaded
        binding.contactScrollview.beVisible()
        if (contact == null) {
            setupNewContact()
        } else {
            setupEditContact()
            originalRingtone = contact?.ringtone
        }

        if (prefill != null) {
            val filled = prefill.applyTo(contact!!, addingNumber) { it.normalizePhoneNumber() }
            contact = filled.contact
            highlightLastPhoneNumber = filled.highlightNumber
            highlightLastEmail = filled.highlightEmail
            setupEditContact()
        }

        setupTypePickers()
        setupRingtone()

        if (contact!!.photoUri.isEmpty() && contact!!.photo == null) {
            showPhotoPlaceholder(binding.contactPhoto)
            binding.contactPhotoBottomShadow.beGone()
        } else {
            updateContactPhoto(contact!!.photoUri, binding.contactPhoto, binding.contactPhotoBottomShadow, contact!!.photo)
        }

        val textColor = getProperTextColor()
        arrayOf(
            binding.contactNameImage,
            binding.contactNumbersImage,
            binding.contactEmailsImage,
            binding.contactAddressesImage,
            binding.contactImsImage,
            binding.contactEventsImage,
            binding.contactNotesImage,
            binding.contactRingtoneImage,
            binding.contactOrganizationImage,
            binding.contactWebsitesImage,
            binding.contactGroupsImage,
            binding.contactSourceImage
        ).forEach {
            it.applyColorFilter(textColor)
        }

        val properPrimaryColor = getProperPrimaryColor()
        arrayOf(
            binding.contactNumbersAddNew, binding.contactEmailsAddNew, binding.contactAddressesAddNew, binding.contactImsAddNew, binding.contactEventsAddNew,
            binding.contactWebsitesAddNew, binding.contactGroupsAddNew
        ).forEach {
            it.applyColorFilter(properPrimaryColor)
        }

        arrayOf(
            binding.contactNumbersAddNew.background,
            binding.contactEmailsAddNew.background,
            binding.contactAddressesAddNew.background,
            binding.contactImsAddNew.background,
            binding.contactEventsAddNew.background,
            binding.contactWebsitesAddNew.background,
            binding.contactGroupsAddNew.background
        ).forEach {
            it.applyColorFilter(textColor)
        }

        binding.contactToggleFavorite.setOnClickListener { toggleFavorite() }
        binding.contactPhoto.setOnClickListener { trySetPhoto() }
        binding.contactChangePhoto.setOnClickListener { trySetPhoto() }
        binding.contactNumbersAddNew.setOnClickListener { addNewPhoneNumberField() }
        binding.contactEmailsAddNew.setOnClickListener { addNewEmailField() }
        binding.contactAddressesAddNew.setOnClickListener { addNewAddressField() }
        binding.contactImsAddNew.setOnClickListener { addNewIMField() }
        binding.contactEventsAddNew.setOnClickListener { addNewEventField() }
        binding.contactWebsitesAddNew.setOnClickListener { addNewWebsiteField() }
        binding.contactGroupsAddNew.setOnClickListener { showSelectGroupsDialog() }
        binding.contactSource.setOnClickListener { showSelectContactSourceDialog() }

        binding.contactChangePhoto.setOnLongClickListener { toast(R.string.change_photo); true; }

        setupFieldVisibility()

        binding.contactToggleFavorite.apply {
            setImageDrawable(getStarDrawable(contact!!.starred == 1))
            tag = contact!!.starred
            setOnLongClickListener { toast(R.string.toggle_favorite); true; }
        }

        val nameTextViews = arrayOf(binding.contactFirstName, binding.contactMiddleName, binding.contactSurname).filter { it.isVisible() }
        if (nameTextViews.isNotEmpty()) {
            setupAutoComplete(nameTextViews)
        }

        updateTextColors(binding.contactScrollview)
        numberViewToColor?.setTextColor(properPrimaryColor)
        emailViewToColor?.setTextColor(properPrimaryColor)
        wasActivityInitialized = true

        binding.contactToolbar.menu.apply {
            findItem(R.id.delete).isVisible = contact?.id != 0
            findItem(R.id.share).isVisible = contact?.id != 0
            findItem(R.id.open_with).isVisible = contact?.id != 0 && contact?.isPrivate() == false
        }
    }

    override fun onBackPressedCompat(): Boolean {
        requestLeave(LeaveVia.BACK)
        return true
    }

    /** Leaving goes through the edit loop: the form is read here, the prompt-or-leave decision is the update's. */
    private fun requestLeave(via: LeaveVia) {
        val facts = BackFacts(isSaving, contact, contact?.let { fillContactValues() }, originalRingtone)
        dispatch(EditContactAction.LeaveRequested(via, facts, System.currentTimeMillis()))
    }

    private fun dispatch(action: EditContactAction) {
        val next = update(editState, action)
        editState = next.state
        if (next.effects.isNotEmpty()) {
            effectRunner.execute { next.effects.forEach { runEffect(it) } }
        }
    }

    private fun post(action: EditContactAction) = runOnUiThread { dispatch(action) }

    private fun runEffect(effect: EditContactEffect) {
        when (effect) {
            is EditContactEffect.Ui -> runOnUiThread { runUi(effect) }
            is EditContactEffect.Load -> runLoad(effect)
        }
    }

    private fun runUi(effect: EditContactEffect.Ui) {
        when (effect) {
            is EditContactEffect.AskToSave -> ConfirmationAdvancedDialog(
                activity = this,
                message = "",
                messageId = org.fossify.commons.R.string.save_before_closing,
                positive = org.fossify.commons.R.string.save,
                negative = org.fossify.commons.R.string.discard
            ) { dispatch(EditContactAction.PromptAnswered(effect.via, it)) }

            is EditContactEffect.Exit -> leave(effect.via)
            EditContactEffect.Save -> saveContact()
            EditContactEffect.AskContactsPermission -> askContactsPermission()
            is EditContactEffect.Notify -> toast(noticeText(effect.notice))
            EditContactEffect.Close -> {
                hideKeyboard()
                finish()
            }

            is EditContactEffect.ShowContact -> gotContact(effect.contact, effect.prefill, effect.addingNumber)
        }
    }

    private fun runLoad(effect: EditContactEffect.Load) {
        when (effect) {
            EditContactEffect.ResolveLookupUri ->
                intent.data?.let { post(EditContactAction.UriResolved(getLookupUriRawId(it))) }
            EditContactEffect.ResolveContactUri ->
                intent.data?.let { post(EditContactAction.UriResolved(getContactUriRawId(it))) }
            is EditContactEffect.LoadContact ->
                post(EditContactAction.ContactLoaded(getContactWithIdOrRaw(effect.id, effect.isPrivate)))
        }
    }

    private fun noticeText(notice: EditContactNotice): Int = when (notice) {
        EditContactNotice.UNKNOWN_ERROR -> org.fossify.commons.R.string.unknown_error_occurred
        EditContactNotice.NO_CONTACTS_PERMISSION -> org.fossify.commons.R.string.no_contacts_permission
    }

    /** Read first, then write; refusing either answers false. */
    private fun askContactsPermission() {
        handlePermission(PERMISSION_READ_CONTACTS) { read ->
            if (read) {
                handlePermission(PERMISSION_WRITE_CONTACTS) { dispatch(EditContactAction.PermissionAnswered(it)) }
            } else {
                dispatch(EditContactAction.PermissionAnswered(false))
            }
        }
    }

    private fun leave(via: LeaveVia) {
        when (via) {
            LeaveVia.BACK -> performDefaultBack()
            LeaveVia.TOOLBAR -> {
                hideKeyboard()
                finish()
            }
        }
    }

    private fun setupMenu() {
        binding.contactToolbar.menu.apply {
            findItem(R.id.save).setOnMenuItemClickListener {
                saveContact()
                true
            }

            findItem(R.id.share).setOnMenuItemClickListener {
                shareContact(contact!!)
                true
            }

            findItem(R.id.open_with).setOnMenuItemClickListener {
                openWith()
                true
            }

            findItem(R.id.delete).setOnMenuItemClickListener {
                deleteContact()
                true
            }

            findItem(R.id.manage_visible_fields).setOnMenuItemClickListener {
                ManageVisibleFieldsDialog(this@EditContactActivity) { changed ->
                    if (changed) applyVisibleFields()
                }
                true
            }
        }

        binding.contactToolbar.setNavigationOnClickListener { requestLeave(LeaveVia.TOOLBAR) }
    }

    private fun openWith() {
        Intent().apply {
            action = Intent.ACTION_EDIT
            data = getContactPublicUri(contact!!)
            launchActivityIntent(this)
        }
    }

    private fun startCropPhotoIntent(primaryUri: Uri?, backupUri: Uri?) {
        if (primaryUri == null) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
            return
        }

        ensureBackgroundThread {
            val imageUri = readableImageUri(primaryUri, backupUri)
            runOnUiThread {
                if (imageUri != null && !isFinishing && !isDestroyed) {
                    launchCropIntent(imageUri)
                }
            }
        }
    }

    /**
     * [primaryUri] if it decodes, else a cache copy of [backupUri]: we may lack permission to pass the backup on
     * (for example an image from Google Photos). Null on failure.
     */
    private fun readableImageUri(primaryUri: Uri, backupUri: Uri?): Uri? {
        if (runCatching { MediaStore.Images.Media.getBitmap(contentResolver, primaryUri) }.getOrNull() != null) {
            return primaryUri
        }

        return try {
            copyToCachePhoto(MediaStore.Images.Media.getBitmap(contentResolver, backupUri))
        } catch (e: Exception) {
            showErrorToast(e)
            null
        }
    }

    /** A cache-file copy of [bitmap] that other apps may read; null when there is no bitmap. */
    private fun copyToCachePhoto(bitmap: Bitmap?): Uri? {
        bitmap ?: return null
        val newFile = getCachePhoto()
        newFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        return getCachePhotoUri(newFile)
    }

    private fun launchCropIntent(imageUri: Uri) {
        hideKeyboard()
        lastPhotoIntentUri = getCachePhotoUri()
        Intent("com.android.camera.action.CROP").apply {
            setDataAndType(imageUri, "image/*")
            putExtra(MediaStore.EXTRA_OUTPUT, lastPhotoIntentUri)
            putExtra("outputX", 512)
            putExtra("outputY", 512)
            putExtra("aspectX", 1)
            putExtra("aspectY", 1)
            putExtra("crop", "true")
            putExtra("scale", "true")
            putExtra("scaleUpIfNeeded", "true")
            clipData = ClipData("Attachment", arrayOf("text/primaryUri-list"), ClipData.Item(lastPhotoIntentUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            try {
                startActivityForResult(this, INTENT_CROP_PHOTO)
            } catch (e: ActivityNotFoundException) {
                toast(org.fossify.commons.R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun setupFieldVisibility() {
        val showFields = config.showContactFields
        if (showFields and (SHOW_PREFIX_FIELD or SHOW_FIRST_NAME_FIELD or SHOW_MIDDLE_NAME_FIELD or SHOW_SURNAME_FIELD or SHOW_SUFFIX_FIELD) == 0) {
            binding.contactNameImage.beInvisible()
        }

        binding.contactPrefix.beVisibleIf(showFields and SHOW_PREFIX_FIELD != 0)
        binding.contactFirstName.beVisibleIf(showFields and SHOW_FIRST_NAME_FIELD != 0)
        binding.contactMiddleName.beVisibleIf(showFields and SHOW_MIDDLE_NAME_FIELD != 0)
        binding.contactSurname.beVisibleIf(showFields and SHOW_SURNAME_FIELD != 0)
        binding.contactSuffix.beVisibleIf(showFields and SHOW_SUFFIX_FIELD != 0)
        binding.contactNickname.beVisibleIf(showFields and SHOW_NICKNAME_FIELD != 0)

        binding.contactSource.beVisibleIf(showFields and SHOW_CONTACT_SOURCE_FIELD != 0)
        binding.contactSourceImage.beVisibleIf(showFields and SHOW_CONTACT_SOURCE_FIELD != 0)

        val arePhoneNumbersVisible = showFields and SHOW_PHONE_NUMBERS_FIELD != 0
        binding.contactNumbersImage.beVisibleIf(arePhoneNumbersVisible)
        binding.contactNumbersHolder.beVisibleIf(arePhoneNumbersVisible)
        binding.contactNumbersAddNew.beVisibleIf(arePhoneNumbersVisible)

        val areEmailsVisible = showFields and SHOW_EMAILS_FIELD != 0
        binding.contactEmailsImage.beVisibleIf(areEmailsVisible)
        binding.contactEmailsHolder.beVisibleIf(areEmailsVisible)
        binding.contactEmailsAddNew.beVisibleIf(areEmailsVisible)

        val areAddressesVisible = showFields and SHOW_ADDRESSES_FIELD != 0
        binding.contactAddressesImage.beVisibleIf(areAddressesVisible)
        binding.contactAddressesHolder.beVisibleIf(areAddressesVisible)
        binding.contactAddressesAddNew.beVisibleIf(areAddressesVisible)

        val areIMsVisible = showFields and SHOW_IMS_FIELD != 0
        binding.contactImsImage.beVisibleIf(areIMsVisible)
        binding.contactImsHolder.beVisibleIf(areIMsVisible)
        binding.contactImsAddNew.beVisibleIf(areIMsVisible)

        val isOrganizationVisible = showFields and SHOW_ORGANIZATION_FIELD != 0
        binding.contactOrganizationCompany.beVisibleIf(isOrganizationVisible)
        binding.contactOrganizationJobPosition.beVisibleIf(isOrganizationVisible)
        binding.contactOrganizationImage.beVisibleIf(isOrganizationVisible)

        val areEventsVisible = showFields and SHOW_EVENTS_FIELD != 0
        binding.contactEventsImage.beVisibleIf(areEventsVisible)
        binding.contactEventsHolder.beVisibleIf(areEventsVisible)
        binding.contactEventsAddNew.beVisibleIf(areEventsVisible)

        val areWebsitesVisible = showFields and SHOW_WEBSITES_FIELD != 0
        binding.contactWebsitesImage.beVisibleIf(areWebsitesVisible)
        binding.contactWebsitesHolder.beVisibleIf(areWebsitesVisible)
        binding.contactWebsitesAddNew.beVisibleIf(areWebsitesVisible)

        val areGroupsVisible = showFields and SHOW_GROUPS_FIELD != 0
        binding.contactGroupsImage.beVisibleIf(areGroupsVisible)
        binding.contactGroupsHolder.beVisibleIf(areGroupsVisible)
        binding.contactGroupsAddNew.beVisibleIf(areGroupsVisible)

        val areNotesVisible = showFields and SHOW_NOTES_FIELD != 0
        binding.contactNotes.beVisibleIf(areNotesVisible)
        binding.contactNotesImage.beVisibleIf(areNotesVisible)

        val isRingtoneVisible = showFields and SHOW_RINGTONE_FIELD != 0
        binding.contactRingtone.beVisibleIf(isRingtoneVisible)
        binding.contactRingtoneImage.beVisibleIf(isRingtoneVisible)
    }

    /** Shows the fields the user chose, keeping what was typed: addresses are rebuilt from the screen, not the DB. */
    private fun applyVisibleFields() {
        val saved = contact ?: return
        contact = fillContactValues()
        binding.contactAddressesHolder.removeAllViews()
        setupAddresses()
        contact = saved
        setupFieldVisibility()
    }

    private fun setupEditContact() {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setupNames()
        setupPhoneNumbers()
        setupEmails()
        setupAddresses()
        setupIMs()
        setupNotes()
        setupOrganization()
        setupWebsites()
        setupEvents()
        setupGroups()
        setupContactSource()
    }

    private fun setupNames() {
        contact!!.apply {
            binding.contactPrefix.setText(prefix)
            binding.contactFirstName.setText(firstName)
            binding.contactMiddleName.setText(middleName)
            binding.contactSurname.setText(surname)
            binding.contactSuffix.setText(suffix)
            binding.contactNickname.setText(nickname)
        }
    }

    private fun setupPhoneNumbers() {
        val phoneNumbers = contact!!.phoneNumbers

        phoneNumbers.forEachIndexed { index, number ->
            val numberHolderView = binding.contactNumbersHolder.getChildAt(index)
            val numberHolder = if (numberHolderView == null) {
                ItemEditPhoneNumberBinding.inflate(layoutInflater, binding.contactNumbersHolder, false).apply {
                    binding.contactNumbersHolder.addView(root)
                }
            } else {
                ItemEditPhoneNumberBinding.bind(numberHolderView)
            }

            numberHolder.apply {
                contactNumber.setText(number.value)
                contactNumber.tag = number.normalizedNumber
                setupPhoneNumberTypePicker(contactNumberType, number.type, number.label)
                if (highlightLastPhoneNumber && index == phoneNumbers.size - 1) {
                    numberViewToColor = contactNumber
                }

                defaultToggleIcon.tag = if (number.isPrimary) 1 else 0
            }
        }

        initNumberHolders()
    }

    private fun setDefaultNumber(selected: ImageView) {
        val numbersCount = binding.contactNumbersHolder.childCount
        for (i in 0 until numbersCount) {
            val toggleIcon = ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(i)).defaultToggleIcon
            if (toggleIcon != selected) {
                toggleIcon.tag = 0
            }
        }

        selected.tag = if (selected.tag == 1) 0 else 1

        initNumberHolders()
    }

    private fun initNumberHolders() {
        val numbersCount = binding.contactNumbersHolder.childCount

        if (numbersCount == 1) {
            ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(0)).defaultToggleIcon.beGone()
            return
        }

        for (i in 0 until numbersCount) {
            val toggleIcon = ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(i)).defaultToggleIcon
            val isPrimary = toggleIcon.tag == 1

            val drawableId = if (isPrimary) {
                org.fossify.commons.R.drawable.ic_star_vector
            } else {
                org.fossify.commons.R.drawable.ic_star_outline_vector
            }

            val drawable = ContextCompat.getDrawable(this@EditContactActivity, drawableId)
            drawable?.apply {
                mutate()
                setTint(getProperTextColor())
            }

            toggleIcon.setImageDrawable(drawable)
            toggleIcon.beVisible()
            toggleIcon.setOnClickListener {
                setDefaultNumber(toggleIcon)
            }
        }
    }

    private fun setupEmails() {
        contact!!.emails.forEachIndexed { index, email ->
            val emailHolderView = binding.contactEmailsHolder.getChildAt(index)
            val emailHolder = if (emailHolderView == null) {
                ItemEditEmailBinding.inflate(layoutInflater, binding.contactEmailsHolder, false).apply {
                    binding.contactEmailsHolder.addView(root)
                }
            } else {
                ItemEditEmailBinding.bind(emailHolderView)
            }

            emailHolder.apply {
                contactEmail.setText(email.value)
                setupEmailTypePicker(contactEmailType, email.type, email.label)
                if (highlightLastEmail && index == contact!!.emails.size - 1) {
                    emailViewToColor = contactEmail
                }
            }
        }
    }

    private fun setupAddresses() {
        if (binding.contactAddressesHolder.childCount > 0 &&
            (binding.contactAddressesHolder.getChildAt(0).id == R.id.contact_structured_address_holder) !=
                (config.showContactFields and SHOW_STRUCTURED_ADDRESSES_FIELD != 0)) {
            // Config was changed with edit view loaded
            binding.contactAddressesHolder.removeAllViews()
        }
        contact!!.addresses.forEachIndexed { index, address ->
            var addressHolderView = binding.contactAddressesHolder.getChildAt(index)
            if (config.showContactFields and SHOW_STRUCTURED_ADDRESSES_FIELD != 0) {
                var structuredAddressHolder = if (addressHolderView == null) {
                    ItemEditStructuredAddressBinding.inflate(layoutInflater, binding.contactAddressesHolder, false).apply {
                        binding.contactAddressesHolder.addView(root)
                    }
                } else {
                    ItemEditStructuredAddressBinding.bind(addressHolderView)
                }

                structuredAddressHolder.apply {
                    contactStreet.setText(address.streetForStructuredEdit())
                    contactNeighborhood.setText(address.neighborhood)
                    contactCity.setText(address.city)
                    contactPostcode.setText(address.postcode)
                    contactPobox.setText(address.pobox)
                    contactRegion.setText(address.region)
                    contactCountry.setText(address.country)
                    setupAddressTypePicker(contactStructuredAddressType, address.type, address.label)
                }
            } else {
                val addressHolder = if (addressHolderView == null) {
                    ItemEditAddressBinding.inflate(layoutInflater, binding.contactAddressesHolder, false).apply {
                        binding.contactAddressesHolder.addView(root)
                    }
                } else {
                    ItemEditAddressBinding.bind(addressHolderView)
                }

                addressHolder.apply {
                    contactAddress.setText(address.value)
                    setupAddressTypePicker(contactAddressType, address.type, address.label)
                }
            }
        }
    }

    private fun setupIMs() {
        contact!!.IMs.forEachIndexed { index, IM ->
            val imHolderView = binding.contactImsHolder.getChildAt(index)
            val imHolder = if (imHolderView == null) {
                ItemEditImBinding.inflate(layoutInflater, binding.contactImsHolder, false).apply {
                    binding.contactImsHolder.addView(root)
                }
            } else {
                ItemEditImBinding.bind(imHolderView)
            }

            imHolder.apply {
                contactIm.setText(IM.value)
                setupIMTypePicker(contactImType, IM.type, IM.label)
            }
        }
    }

    private fun setupNotes() {
        binding.contactNotes.setText(contact!!.notes)
    }

    private fun setupRingtone() {
        binding.contactRingtone.setOnClickListener {
            hideKeyboard()
            val ringtonePickerIntent = getRingtonePickerIntent()
            try {
                startActivityForResult(ringtonePickerIntent, INTENT_SELECT_RINGTONE)
            } catch (e: Exception) {
                val currentRingtone = contact!!.ringtone ?: getDefaultAlarmSound(RingtoneManager.TYPE_RINGTONE).uri
                SelectAlarmSoundDialog(this, currentRingtone, AudioManager.STREAM_RING, PICK_RINGTONE_INTENT_ID, RingtoneManager.TYPE_RINGTONE, true,
                    onAlarmPicked = {
                        contact!!.ringtone = it?.uri
                        binding.contactRingtone.text = it?.title
                    }, onAlarmSoundDeleted = {}
                )
            }
        }

        val ringtone = contact!!.ringtone
        if (ringtone?.isEmpty() == true) {
            binding.contactRingtone.text = getString(org.fossify.commons.R.string.no_sound)
        } else if (ringtone?.isNotEmpty() == true) {
            if (ringtone == SILENT) {
                binding.contactRingtone.text = getString(org.fossify.commons.R.string.no_sound)
            } else {
                systemRingtoneSelected(Uri.parse(ringtone))
            }
        } else {
            val default = getDefaultAlarmSound(RingtoneManager.TYPE_RINGTONE)
            binding.contactRingtone.text = default.title
        }
    }

    private fun setupOrganization() {
        binding.contactOrganizationCompany.setText(contact!!.organization.company)
        binding.contactOrganizationJobPosition.setText(contact!!.organization.jobPosition)
    }

    private fun setupWebsites() {
        contact!!.websites.forEachIndexed { index, website ->
            val websitesHolderView = binding.contactWebsitesHolder.getChildAt(index)
            val websitesHolder = if (websitesHolderView == null) {
                ItemEditWebsiteBinding.inflate(layoutInflater, binding.contactWebsitesHolder, false).apply {
                    binding.contactWebsitesHolder.addView(root)
                }
            } else {
                ItemEditWebsiteBinding.bind(websitesHolderView)
            }

            websitesHolder.contactWebsite.setText(website)
        }
    }

    private fun setupEvents() {
        contact!!.events.forEachIndexed { index, event ->
            val eventHolderView = binding.contactEventsHolder.getChildAt(index)
            val eventHolder = if (eventHolderView == null) {
                ItemEventBinding.inflate(layoutInflater, binding.contactEventsHolder, false).apply {
                    binding.contactEventsHolder.addView(root)
                }
            } else {
                ItemEventBinding.bind(eventHolderView)
            }

            eventHolder.apply {
                val contactEvent = contactEvent.apply {
                    event.value.getDateTimeFromDateString(true, this)
                    tag = event.value
                    alpha = 1f
                }

                setupEventTypePicker(this, event.type)

                contactEventRemove.apply {
                    beVisible()
                    applyColorFilter(getProperPrimaryColor())
                    background.applyColorFilter(getProperTextColor())
                    setOnClickListener {
                        resetContactEvent(contactEvent, this)
                    }
                }
            }
        }
    }

    private fun setupGroups() {
        binding.contactGroupsHolder.removeAllViews()
        val groups = contact!!.groups
        groups.forEachIndexed { index, group ->
            val groupHolderView = binding.contactGroupsHolder.getChildAt(index)
            val groupHolder = if (groupHolderView == null) {
                ItemEditGroupBinding.inflate(layoutInflater, binding.contactGroupsHolder, false).apply {
                    binding.contactGroupsHolder.addView(root)
                }
            } else {
                ItemEditGroupBinding.bind(groupHolderView)
            }

            groupHolder.apply {
                contactGroup.apply {
                    text = group.title
                    setTextColor(getProperTextColor())
                    tag = group.id
                    alpha = 1f
                }

                root.setOnClickListener {
                    showSelectGroupsDialog()
                }

                contactGroupRemove.apply {
                    beVisible()
                    applyColorFilter(getProperPrimaryColor())
                    background.applyColorFilter(getProperTextColor())
                    setOnClickListener {
                        removeGroup(group.id!!)
                    }
                }
            }
        }

        if (groups.isEmpty()) {
            ItemEditGroupBinding.inflate(layoutInflater, binding.contactGroupsHolder, false).apply {
                contactGroup.apply {
                    alpha = 0.5f
                    text = getString(R.string.no_groups)
                    setTextColor(getProperTextColor())
                }

                binding.contactGroupsHolder.addView(root)
                contactGroupRemove.beGone()
                root.setOnClickListener {
                    showSelectGroupsDialog()
                }
            }
        }
    }

    private fun setupContactSource() {
        originalContactSource = contact!!.source
        getPublicContactSource(contact!!.source) {
            binding.contactSource.text = if (it == "") getString(R.string.phone_storage) else it
        }
    }

    private fun setupNewContact() {
        originalContactSource = if (hasContactPermissions()) config.lastUsedContactSource else SMT_PRIVATE
        contact = getEmptyContact()
        getPublicContactSource(contact!!.source) {
            binding.contactSource.text = if (it == "") getString(R.string.phone_storage) else it
        }

        // if the last used contact source is not available anymore, use the first available one. Could happen at ejecting SIM card
        ContactsHelper(this).getSaveableContactSources { sources ->
            val sourceNames = sources.map { it.name }
            if (!sourceNames.contains(originalContactSource)) {
                originalContactSource = sourceNames.first()
                contact?.source = originalContactSource
                getPublicContactSource(contact!!.source) {
                    binding.contactSource.text = if (it == "") getString(R.string.phone_storage) else it
                }
            }
        }
    }

    private fun setupTypePickers() {
        if (contact!!.phoneNumbers.isEmpty()) {
            val numberHolder = ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(0))
            numberHolder.contactNumberType.apply {
                setupPhoneNumberTypePicker(this, DEFAULT_PHONE_NUMBER_TYPE, "")
            }
        }

        if (contact!!.emails.isEmpty()) {
            val emailHolder = ItemEditEmailBinding.bind(binding.contactEmailsHolder.getChildAt(0))
            emailHolder.contactEmailType.apply {
                setupEmailTypePicker(this, DEFAULT_EMAIL_TYPE, "")
            }
        }

        if (contact!!.addresses.isEmpty()) {
            addNewAddressField()
        }

        if (contact!!.IMs.isEmpty()) {
            val IMHolder = ItemEditImBinding.bind(binding.contactImsHolder.getChildAt(0))
            IMHolder.contactImType.apply {
                setupIMTypePicker(this, DEFAULT_IM_TYPE, "")
            }
        }

        if (contact!!.events.isEmpty()) {
            val eventHolder = ItemEventBinding.bind(binding.contactEventsHolder.getChildAt(0))
            eventHolder.apply {
                setupEventTypePicker(this)
            }
        }

        if (contact!!.groups.isEmpty()) {
            val groupsHolder = ItemEditGroupBinding.bind(binding.contactGroupsHolder.getChildAt(0))
            groupsHolder.contactGroup.apply {
                setupGroupsPicker(this)
            }
        }
    }

    private fun setupPhoneNumberTypePicker(numberTypeField: TextView, type: Int, label: String) {
        numberTypeField.apply {
            text = getPhoneNumberTypeText(type, label)
            tag = type
            setOnClickListener {
                showNumberTypePicker(it as TextView)
            }
        }
    }

    private fun setupEmailTypePicker(emailTypeField: TextView, type: Int, label: String) {
        emailTypeField.apply {
            text = getEmailTypeText(type, label)
            tag = type
            setOnClickListener {
                showEmailTypePicker(it as TextView)
            }
        }
    }

    private fun setupAddressTypePicker(addressTypeField: TextView, type: Int, label: String) {
        addressTypeField.apply {
            text = getAddressTypeText(type, label)
            tag = type
            setOnClickListener {
                showAddressTypePicker(it as TextView)
            }
        }
    }

    private fun setupIMTypePicker(imTypeField: TextView, type: Int, label: String) {
        imTypeField.apply {
            text = getIMTypeText(type, label)
            tag = type
            setOnClickListener {
                showIMTypePicker(it as TextView)
            }
        }
    }

    private fun setupEventTypePicker(eventHolder: ItemEventBinding, type: Int = DEFAULT_EVENT_TYPE) {
        eventHolder.contactEventType.apply {
            setText(getEventTextId(type))
            tag = type
            setOnClickListener {
                showEventTypePicker(it as TextView)
            }
        }

        val eventField = eventHolder.contactEvent
        eventField.setOnClickListener {
            MyDatePickerDialog(this, eventField.tag?.toString() ?: "") { dateTag ->
                eventField.apply {
                    dateTag.getDateTimeFromDateString(true, this)
                    tag = dateTag
                    alpha = 1f
                }
            }
        }

        eventHolder.contactEventRemove.apply {
            applyColorFilter(getProperPrimaryColor())
            background.applyColorFilter(getProperTextColor())
            setOnClickListener {
                resetContactEvent(eventField, this@apply)
            }
        }
    }

    private fun setupGroupsPicker(groupTitleField: TextView, group: Group? = null) {
        groupTitleField.apply {
            text = group?.title ?: getString(R.string.no_groups)
            alpha = if (group == null) 0.5f else 1f
            setOnClickListener {
                showSelectGroupsDialog()
            }
        }
    }

    private fun resetContactEvent(contactEvent: TextView, removeContactEventButton: ImageView) {
        contactEvent.apply {
            text = getString(org.fossify.commons.R.string.unknown)
            tag = ""
            alpha = 0.5f
        }
        removeContactEventButton.beGone()
    }

    private fun removeGroup(id: Long) {
        contact!!.groups = contact!!.groups.filter { it.id != id } as ArrayList<Group>
        setupGroups()
    }

    private fun showNumberTypePicker(numberTypeField: TextView) {
        val items = arrayListOf(
            RadioItem(Phone.TYPE_MOBILE, getString(org.fossify.commons.R.string.mobile)),
            RadioItem(Phone.TYPE_HOME, getString(org.fossify.commons.R.string.home)),
            RadioItem(Phone.TYPE_WORK, getString(org.fossify.commons.R.string.work)),
            RadioItem(Phone.TYPE_MAIN, getString(org.fossify.commons.R.string.main_number)),
            RadioItem(Phone.TYPE_FAX_WORK, getString(org.fossify.commons.R.string.work_fax)),
            RadioItem(Phone.TYPE_FAX_HOME, getString(org.fossify.commons.R.string.home_fax)),
            RadioItem(Phone.TYPE_PAGER, getString(org.fossify.commons.R.string.pager)),
            RadioItem(Phone.TYPE_OTHER, getString(org.fossify.commons.R.string.other)),
            RadioItem(Phone.TYPE_CUSTOM, getString(org.fossify.commons.R.string.custom))
        )

        val currentNumberTypeId = typeOf(numberTypeField, PHONE_TYPE_LABELS, Phone.TYPE_CUSTOM)
        RadioGroupDialog(this, items, currentNumberTypeId) {
            if (it as Int == Phone.TYPE_CUSTOM) {
                CustomLabelDialog(this) {
                    numberTypeField.text = it
                    numberTypeField.tag = Phone.TYPE_CUSTOM
                }
            } else {
                numberTypeField.text = getPhoneNumberTypeText(it, "")
                numberTypeField.tag = it
            }
        }
    }

    private fun showEmailTypePicker(emailTypeField: TextView) {
        val items = arrayListOf(
            RadioItem(CommonDataKinds.Email.TYPE_HOME, getString(org.fossify.commons.R.string.home)),
            RadioItem(CommonDataKinds.Email.TYPE_WORK, getString(org.fossify.commons.R.string.work)),
            RadioItem(CommonDataKinds.Email.TYPE_MOBILE, getString(org.fossify.commons.R.string.mobile)),
            RadioItem(CommonDataKinds.Email.TYPE_OTHER, getString(org.fossify.commons.R.string.other)),
            RadioItem(CommonDataKinds.Email.TYPE_CUSTOM, getString(org.fossify.commons.R.string.custom))
        )

        val currentEmailTypeId = typeOf(emailTypeField, EMAIL_TYPE_LABELS, CommonDataKinds.Email.TYPE_CUSTOM)
        RadioGroupDialog(this, items, currentEmailTypeId) {
            if (it as Int == CommonDataKinds.Email.TYPE_CUSTOM) {
                CustomLabelDialog(this) {
                    emailTypeField.text = it
                    emailTypeField.tag = CommonDataKinds.Email.TYPE_CUSTOM
                }
            } else {
                emailTypeField.text = getEmailTypeText(it, "")
                emailTypeField.tag = it
            }
        }
    }

    private fun showAddressTypePicker(addressTypeField: TextView) {
        val items = arrayListOf(
            RadioItem(StructuredPostal.TYPE_HOME, getString(org.fossify.commons.R.string.home)),
            RadioItem(StructuredPostal.TYPE_WORK, getString(org.fossify.commons.R.string.work)),
            RadioItem(StructuredPostal.TYPE_OTHER, getString(org.fossify.commons.R.string.other)),
            RadioItem(StructuredPostal.TYPE_CUSTOM, getString(org.fossify.commons.R.string.custom))
        )

        val currentAddressTypeId = typeOf(addressTypeField, ADDRESS_TYPE_LABELS, StructuredPostal.TYPE_CUSTOM)
        RadioGroupDialog(this, items, currentAddressTypeId) {
            if (it as Int == StructuredPostal.TYPE_CUSTOM) {
                CustomLabelDialog(this) {
                    addressTypeField.text = it
                    addressTypeField.tag = StructuredPostal.TYPE_CUSTOM
                }
            } else {
                addressTypeField.text = getAddressTypeText(it, "")
                addressTypeField.tag = it
            }
        }
    }

    private fun showIMTypePicker(imTypeField: TextView) {
        val items = arrayListOf(
            RadioItem(Im.PROTOCOL_AIM, getString(R.string.aim)),
            RadioItem(Im.PROTOCOL_MSN, getString(R.string.windows_live)),
            RadioItem(Im.PROTOCOL_YAHOO, getString(R.string.yahoo)),
            RadioItem(Im.PROTOCOL_SKYPE, getString(R.string.skype)),
            RadioItem(Im.PROTOCOL_QQ, getString(R.string.qq)),
            RadioItem(Im.PROTOCOL_GOOGLE_TALK, getString(R.string.hangouts)),
            RadioItem(Im.PROTOCOL_ICQ, getString(R.string.icq)),
            RadioItem(Im.PROTOCOL_JABBER, getString(R.string.jabber)),
            RadioItem(Im.PROTOCOL_CUSTOM, getString(org.fossify.commons.R.string.custom))
        )

        val currentIMTypeId = typeOf(imTypeField, IM_TYPE_LABELS, Im.PROTOCOL_CUSTOM)
        RadioGroupDialog(this, items, currentIMTypeId) {
            if (it as Int == Im.PROTOCOL_CUSTOM) {
                CustomLabelDialog(this) {
                    imTypeField.text = it
                    imTypeField.tag = Im.PROTOCOL_CUSTOM
                }
            } else {
                imTypeField.text = getIMTypeText(it, "")
                imTypeField.tag = it
            }
        }
    }

    private fun showEventTypePicker(eventTypeField: TextView) {
        val items = arrayListOf(
            RadioItem(CommonDataKinds.Event.TYPE_ANNIVERSARY, getString(org.fossify.commons.R.string.anniversary)),
            RadioItem(CommonDataKinds.Event.TYPE_BIRTHDAY, getString(org.fossify.commons.R.string.birthday)),
            RadioItem(CommonDataKinds.Event.TYPE_OTHER, getString(org.fossify.commons.R.string.other))
        )

        val currentEventTypeId = typeOf(eventTypeField, EVENT_TYPE_LABELS, CommonDataKinds.Event.TYPE_OTHER)
        RadioGroupDialog(this, items, currentEventTypeId) {
            eventTypeField.setText(getEventTextId(it as Int))
            eventTypeField.tag = it
        }
    }

    private fun showSelectGroupsDialog() {
        SelectGroupsDialog(this@EditContactActivity, contact!!.groups) {
            contact!!.groups = it
            setupGroups()
        }
    }

    private fun showSelectContactSourceDialog() {
        showContactSourcePicker(contact!!.source) {
            contact!!.source = if (it == getString(R.string.phone_storage_hidden)) SMT_PRIVATE else it
            getPublicContactSource(it) {
                binding.contactSource.text = if (it == "") getString(R.string.phone_storage) else it
            }
        }
    }

    private fun saveContact() {
        if (isSaving || contact == null) {
            return
        }

        if (readForm().isBlank(unknownEventText())) {
            toast(R.string.fields_empty)
            return
        }

        val contactValues = fillContactValues()

        val oldPhotoUri = contact!!.photoUri
        val oldPrimary = contact!!.phoneNumbers.find { it.isPrimary }
        val newPrimary = contactValues.phoneNumbers.find { it.isPrimary }
        val primaryState = Pair(oldPrimary, newPrimary)

        val previous = contact
        isSaving = true
        contact = contactValues

        ensureBackgroundThread {
            config.lastUsedContactSource = contact!!.source
            when {
                contact!!.id == 0 -> insertNewContact(false, previous)
                originalContactSource != contact!!.source -> insertNewContact(true, previous)
                else -> {
                    val photoUpdateStatus = getPhotoUpdateStatus(oldPhotoUri, contact!!.photoUri)
                    updateContact(photoUpdateStatus, primaryState, previous)
                }
            }
        }
    }

    private fun fillContactValues(): Contact = readForm().toContact(
        contact!!, unknownEventText(), { it.normalizePhoneNumber() }, { a, b -> PhoneNumberUtils.compare(a, b) }
    )

    private fun readForm() = FormSnapshot(
        prefix = binding.contactPrefix.value, firstName = binding.contactFirstName.value,
        middleName = binding.contactMiddleName.value, surname = binding.contactSurname.value,
        suffix = binding.contactSuffix.value, nickname = binding.contactNickname.value,
        notes = binding.contactNotes.value, company = binding.contactOrganizationCompany.value,
        jobPosition = binding.contactOrganizationJobPosition.value,
        photoPath = currentContactPhotoPath, starred = isContactStarred(),
        numbers = readNumberRows(), emails = readEmailRows(), addresses = readAddressRows(),
        ims = readImRows(), events = readEventRows(), websites = readWebsites(),
    )

    private fun unknownEventText() = getString(org.fossify.commons.R.string.unknown)

    private fun readNumberRows(): List<NumberRow> = (0 until binding.contactNumbersHolder.childCount).map { i ->
        val numberHolder = ItemEditPhoneNumberBinding.bind(binding.contactNumbersHolder.getChildAt(i))
        NumberRow(
            number = numberHolder.contactNumber.value,
            type = typeOf(numberHolder.contactNumberType, PHONE_TYPE_LABELS, Phone.TYPE_CUSTOM),
            label = numberHolder.contactNumberType.value,
            // the normalised number the row was loaded with; the save keeps it when the number is unchanged
            fetchedNormalized = numberHolder.contactNumber.tag?.toString() ?: "",
            isPrimary = numberHolder.defaultToggleIcon.tag == 1,
        )
    }

    private fun readEmailRows(): List<TypedRow> = (0 until binding.contactEmailsHolder.childCount).map { i ->
        val emailHolder = ItemEditEmailBinding.bind(binding.contactEmailsHolder.getChildAt(i))
        TypedRow(
            value = emailHolder.contactEmail.value,
            type = typeOf(emailHolder.contactEmailType, EMAIL_TYPE_LABELS, CommonDataKinds.Email.TYPE_CUSTOM),
            label = emailHolder.contactEmailType.value,
        )
    }

    private fun readAddressRows(): List<AddressRow> = (0 until binding.contactAddressesHolder.childCount).map { i ->
        val addressHolderView = binding.contactAddressesHolder.getChildAt(i)
        // Don't rely on config.showContactFields since that might just have changed
        if (addressHolderView.id == R.id.contact_structured_address_holder) {
            val structuredAddressHolder = ItemEditStructuredAddressBinding.bind(addressHolderView)
            StructuredAddress(
                street = structuredAddressHolder.contactStreet.value,
                neighborhood = structuredAddressHolder.contactNeighborhood.value,
                city = structuredAddressHolder.contactCity.value,
                postcode = structuredAddressHolder.contactPostcode.value,
                pobox = structuredAddressHolder.contactPobox.value,
                region = structuredAddressHolder.contactRegion.value,
                country = structuredAddressHolder.contactCountry.value,
                type = typeOf(
                    structuredAddressHolder.contactStructuredAddressType,
                    ADDRESS_TYPE_LABELS,
                    StructuredPostal.TYPE_CUSTOM,
                ),
                label = structuredAddressHolder.contactStructuredAddressType.value,
            )
        } else {
            val addressHolder = ItemEditAddressBinding.bind(addressHolderView)
            PlainAddress(
                address = addressHolder.contactAddress.value,
                type = typeOf(addressHolder.contactAddressType, ADDRESS_TYPE_LABELS, StructuredPostal.TYPE_CUSTOM),
                label = addressHolder.contactAddressType.value,
            )
        }
    }

    private fun readImRows(): List<TypedRow> = (0 until binding.contactImsHolder.childCount).map { i ->
        val imHolder = ItemEditImBinding.bind(binding.contactImsHolder.getChildAt(i))
        TypedRow(
            value = imHolder.contactIm.value,
            type = typeOf(imHolder.contactImType, IM_TYPE_LABELS, Im.PROTOCOL_CUSTOM),
            label = imHolder.contactImType.value,
        )
    }

    private fun readEventRows(): List<EventRow> = (0 until binding.contactEventsHolder.childCount).map { i ->
        val eventHolder = ItemEventBinding.bind(binding.contactEventsHolder.getChildAt(i))
        EventRow(
            shown = eventHolder.contactEvent.value,
            date = eventHolder.contactEvent.tag?.toString() ?: "",
            type = typeOf(eventHolder.contactEventType, EVENT_TYPE_LABELS, CommonDataKinds.Event.TYPE_OTHER),
        )
    }

    private fun readWebsites(): List<String> = (0 until binding.contactWebsitesHolder.childCount).map { i ->
        ItemEditWebsiteBinding.bind(binding.contactWebsitesHolder.getChildAt(i)).contactWebsite.value
    }

    private fun insertNewContact(deleteCurrentContact: Boolean, previous: Contact?) {
        if (!deleteCurrentContact) {
            toast(R.string.inserting)
        }

        if (ContactsHelper(this@EditContactActivity).insertContact(contact!!)) {
            if (deleteCurrentContact) {
                contact!!.source = originalContactSource
                ContactsHelper(this).deleteContact(contact!!, false) {
                    finishSaved()
                }
            } else {
                finishSaved()
            }
        } else {
            saveFailed(previous)
        }
    }

    private fun updateContact(
        photoUpdateStatus: Int,
        primaryState: Pair<PhoneNumber?, PhoneNumber?>,
        previous: Contact?
    ) {
        RawContactLoader(this).ensureNameRow(contact!!)
        if (ContactsHelper(this@EditContactActivity).updateContact(contact!!, photoUpdateStatus)) {
            val status = getPrimaryNumberStatus(primaryState.first, primaryState.second)
            if (status != PrimaryNumberStatus.UNCHANGED) {
                updateDefaultNumberForDuplicateContacts(primaryState, status) {
                    finishSaved()
                }
            } else {
                finishSaved()
            }
        } else {
            saveFailed(previous)
        }
    }

    private fun finishSaved() = runOnUiThread {
        setResult(Activity.RESULT_OK)
        hideKeyboard()
        finish()
    }

    /** Puts back the contact from before the save, so the edits still count as unsaved, and allows another try. */
    private fun saveFailed(previous: Contact?) = runOnUiThread {
        contact = previous
        isSaving = false
        toast(org.fossify.commons.R.string.unknown_error_occurred)
    }

    private fun updateDefaultNumberForDuplicateContacts(
        toggleState: Pair<PhoneNumber?, PhoneNumber?>,
        primaryStatus: PrimaryNumberStatus,
        callback: () -> Unit
    ) {
        val contactsHelper = ContactsHelper(this)

        getDuplicatesKeptTogether(contact!!, false) { contacts ->
            ensureBackgroundThread {
                val displayContactSources = getVisibleContactSources()
                contacts.filter { displayContactSources.contains(it.source) }.forEach { contact ->
                    val duplicate = getContactWithIdOrRaw(contact.id, contact.isPrivate())
                    if (duplicate != null) {
                        if (primaryStatus == PrimaryNumberStatus.UNSTARRED) {
                            val number = duplicate.phoneNumbers.find { it.normalizedNumber == toggleState.first!!.normalizedNumber }
                            number?.isPrimary = false
                        } else if (primaryStatus == PrimaryNumberStatus.STARRED) {
                            val number = duplicate.phoneNumbers.find { it.normalizedNumber == toggleState.second!!.normalizedNumber }
                            if (number != null) {
                                duplicate.phoneNumbers.forEach {
                                    it.isPrimary = false
                                }
                                number.isPrimary = true
                            }
                        }

                        contactsHelper.updateContact(duplicate, PHOTO_UNCHANGED)
                    }
                }

                runOnUiThread {
                    callback.invoke()
                }
            }
        }
    }

    private fun getPrimaryNumberStatus(oldPrimary: PhoneNumber?, newPrimary: PhoneNumber?): PrimaryNumberStatus {
        return if (oldPrimary != null && newPrimary != null && oldPrimary != newPrimary) {
            PrimaryNumberStatus.STARRED
        } else if (oldPrimary == null && newPrimary != null) {
            PrimaryNumberStatus.STARRED
        } else if (oldPrimary != null && newPrimary == null) {
            PrimaryNumberStatus.UNSTARRED
        } else {
            PrimaryNumberStatus.UNCHANGED
        }
    }

    private fun getPhotoUpdateStatus(oldUri: String, newUri: String): Int {
        return if (oldUri.isEmpty() && newUri.isNotEmpty()) {
            PHOTO_ADDED
        } else if (oldUri.isNotEmpty() && newUri.isEmpty()) {
            PHOTO_REMOVED
        } else if (oldUri != newUri) {
            PHOTO_CHANGED
        } else {
            PHOTO_UNCHANGED
        }
    }

    private fun addNewPhoneNumberField() {
        val numberHolder = ItemEditPhoneNumberBinding.inflate(layoutInflater, binding.contactNumbersHolder, false)
        updateTextColors(numberHolder.root)
        setupPhoneNumberTypePicker(numberHolder.contactNumberType, DEFAULT_PHONE_NUMBER_TYPE, "")
        binding.contactNumbersHolder.addView(numberHolder.root)
        binding.contactNumbersHolder.onGlobalLayout {
            numberHolder.contactNumber.requestFocus()
            showKeyboard(numberHolder.contactNumber)
        }
        numberHolder.defaultToggleIcon.tag = 0
        initNumberHolders()
    }

    private fun addNewEmailField() {
        val emailHolder = ItemEditEmailBinding.inflate(layoutInflater, binding.contactEmailsHolder, false)
        updateTextColors(emailHolder.root)
        setupEmailTypePicker(emailHolder.contactEmailType, DEFAULT_EMAIL_TYPE, "")
        binding.contactEmailsHolder.addView(emailHolder.root)
        binding.contactEmailsHolder.onGlobalLayout {
            emailHolder.contactEmail.requestFocus()
            showKeyboard(emailHolder.contactEmail)
        }
    }

    private fun addNewAddressField() {
        if (config.showContactFields and SHOW_STRUCTURED_ADDRESSES_FIELD != 0) {
            val structutedAddressHolder = ItemEditStructuredAddressBinding.inflate(layoutInflater,
                binding.contactAddressesHolder, false)
            updateTextColors(structutedAddressHolder.root)
            setupAddressTypePicker(structutedAddressHolder.contactStructuredAddressType, DEFAULT_ADDRESS_TYPE, "")
            binding.contactAddressesHolder.addView(structutedAddressHolder.root)
            binding.contactAddressesHolder.onGlobalLayout {
                structutedAddressHolder.contactStreet.requestFocus()
                showKeyboard(structutedAddressHolder.contactStreet)
            }
        } else {
            val addressHolder = ItemEditAddressBinding.inflate(layoutInflater, binding.contactAddressesHolder, false)
            updateTextColors(addressHolder.root)
            setupAddressTypePicker(addressHolder.contactAddressType, DEFAULT_ADDRESS_TYPE, "")
            binding.contactAddressesHolder.addView(addressHolder.root)
            binding.contactAddressesHolder.onGlobalLayout {
                addressHolder.contactAddress.requestFocus()
                showKeyboard(addressHolder.contactAddress)
            }
        }
    }

    private fun addNewIMField() {
        val IMHolder = ItemEditImBinding.inflate(layoutInflater, binding.contactImsHolder, false)
        updateTextColors(IMHolder.root)
        setupIMTypePicker(IMHolder.contactImType, DEFAULT_IM_TYPE, "")
        binding.contactImsHolder.addView(IMHolder.root)
        binding.contactImsHolder.onGlobalLayout {
            IMHolder.contactIm.requestFocus()
            showKeyboard(IMHolder.contactIm)
        }
    }

    private fun addNewEventField() {
        val eventHolder = ItemEventBinding.inflate(layoutInflater, binding.contactEventsHolder, false)
        updateTextColors(eventHolder.root)
        setupEventTypePicker(eventHolder)
        binding.contactEventsHolder.addView(eventHolder.root)
    }

    private fun toggleFavorite() {
        val isStarred = isContactStarred()
        binding.contactToggleFavorite.apply {
            setImageDrawable(getStarDrawable(!isStarred))
            tag = if (isStarred) 0 else 1

            setOnLongClickListener { toast(R.string.toggle_favorite); true; }
        }
    }

    private fun addNewWebsiteField() {
        val websitesHolder = ItemEditWebsiteBinding.inflate(layoutInflater, binding.contactWebsitesHolder, false)
        updateTextColors(websitesHolder.root)
        binding.contactWebsitesHolder.addView(websitesHolder.root)
        binding.contactWebsitesHolder.onGlobalLayout {
            websitesHolder.contactWebsite.requestFocus()
            showKeyboard(websitesHolder.contactWebsite)
        }
    }

    private fun isContactStarred() = binding.contactToggleFavorite.tag == 1

    private fun getStarDrawable(on: Boolean) =
        resources.getDrawable(if (on) org.fossify.commons.R.drawable.ic_star_vector else org.fossify.commons.R.drawable.ic_star_outline_vector)

    private fun trySetPhoto() {
        val items = arrayListOf(
            RadioItem(TAKE_PHOTO, getString(org.fossify.commons.R.string.take_photo)),
            RadioItem(CHOOSE_PHOTO, getString(org.fossify.commons.R.string.choose_photo))
        )

        if (currentContactPhotoPath.isNotEmpty() || contact!!.photo != null) {
            items.add(RadioItem(REMOVE_PHOTO, getString(R.string.remove_photo)))
        }

        RadioGroupDialog(this, items) {
            when (it as Int) {
                TAKE_PHOTO -> startTakePhotoIntent()
                CHOOSE_PHOTO -> startChoosePhotoIntent()
                else -> {
                    showPhotoPlaceholder(binding.contactPhoto)
                    binding.contactPhotoBottomShadow.beGone()
                }
            }
        }
    }

    private fun startTakePhotoIntent() {
        hideKeyboard()
        val uri = getCachePhotoUri()
        lastPhotoIntentUri = uri
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)

            try {
                startActivityForResult(this, INTENT_TAKE_PHOTO)
            } catch (e: ActivityNotFoundException) {
                toast(org.fossify.commons.R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun startChoosePhotoIntent() {
        hideKeyboard()
        val uri = getCachePhotoUri()
        lastPhotoIntentUri = uri
        Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
            clipData = ClipData("Attachment", arrayOf("text/uri-list"), ClipData.Item(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            putExtra(MediaStore.EXTRA_OUTPUT, uri)

            try {
                startActivityForResult(this, INTENT_CHOOSE_PHOTO)
            } catch (e: ActivityNotFoundException) {
                toast(org.fossify.commons.R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    override fun customRingtoneSelected(ringtonePath: String) {
        contact!!.ringtone = ringtonePath
        binding.contactRingtone.text = ringtonePath.getFilenameFromPath()
    }

    override fun systemRingtoneSelected(uri: Uri?) {
        contact!!.ringtone = uri?.toString() ?: ""
        val contactRingtone = RingtoneManager.getRingtone(this, uri)
        binding.contactRingtone.text = contactRingtone.getTitle(this)
    }

    /** The type id the row's type field holds; [labels] and [fallback] map its text when no picker set it. */
    private fun typeOf(typeField: TextView, labels: TypeLabels, fallback: Int): Int =
        typeField.tag as? Int ?: typeIdOf(typeField.value, labels, fallback, ::getString)

    private fun setupAutoComplete(nameTextViews: List<MyAutoCompleteTextView>) {
        ContactsHelper(this).getContacts { contacts ->
            val adapter = AutoCompleteTextViewAdapter(this, contacts)
            val handler = Handler(mainLooper)
            nameTextViews.forEach { view ->
                view.setAdapter(adapter)
                view.setOnItemClickListener { _, _, position, _ ->
                    val selectedContact = adapter.resultList[position]

                    if (binding.contactFirstName.isVisible()) {
                        binding.contactFirstName.setText(selectedContact.firstName)
                    }
                    if (binding.contactMiddleName.isVisible()) {
                        binding.contactMiddleName.setText(selectedContact.middleName)
                    }
                    if (binding.contactSurname.isVisible()) {
                        binding.contactSurname.setText(selectedContact.surname)
                    }
                }
                view.doAfterTextChanged {
                    handler.postDelayed({
                        adapter.autoComplete = true
                        adapter.filter.filter(it)
                    }, AUTO_COMPLETE_DELAY)
                }
            }
        }
    }
}

/**
 * Copyright (C) 2012 Moxie Marlinspike
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.bcm.messenger.common.deprecated;

import android.content.Context;
import androidx.annotation.NonNull;
import android.text.SpannableString;

import com.bcm.messenger.common.R;
import com.bcm.messenger.common.deprecated.SmsDatabase.Status;
import com.bcm.messenger.common.database.documents.IdentityKeyMismatch;
import com.bcm.messenger.common.database.documents.NetworkFailure;
import com.bcm.messenger.common.mms.SlideDeck;
import com.bcm.messenger.common.recipients.Recipient;

import java.util.List;

/**
 * Represents the message record model for MMS messages that contain
 * media (ie: they've been downloaded).
 *
 * @author Moxie Marlinspike
 *
 */

@Deprecated
public class MediaMmsMessageRecord extends MmsMessageRecord {
  private final static String TAG = MediaMmsMessageRecord.class.getSimpleName();

  private final Context context;
  private final int     partCount;

  public MediaMmsMessageRecord(Context context, long id, Recipient conversationRecipient,
                               Recipient individualRecipient, int recipientDeviceId,
                               long dateSent, long dateReceived, int deliveryReceiptCount,
                               long threadId, Body body,
                               @NonNull SlideDeck slideDeck,
                               int partCount, long mailbox,
                               List<IdentityKeyMismatch> mismatches,
                               List<NetworkFailure> failures, int subscriptionId,
                               long expiresIn, long expireStarted, int readReceiptCount)
  {
    super(context, id, body, conversationRecipient, individualRecipient, recipientDeviceId, dateSent,
          dateReceived, threadId, Status.STATUS_NONE, deliveryReceiptCount, mailbox, mismatches, failures,
          subscriptionId, expiresIn, expireStarted, slideDeck, readReceiptCount);

    this.context   = context.getApplicationContext();
    this.partCount = partCount;
  }

  public int getPartCount() {
    return partCount;
  }

  @Override
  public boolean isMmsNotification() {
    return false;
  }

  @Override
  public SpannableString getDisplayBody() {
      if (MmsDatabase.Types.isLocationType(type)) {
          return new SpannableString("[Location Message]");
      }
    if (MmsDatabase.Types.isDecryptInProgressType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_decrypting_mms_please_wait));
    } else if (MmsDatabase.Types.isFailedDecryptType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_bad_encrypted_mms_message));
    } else if (MmsDatabase.Types.isDuplicateMessageType(type)) {
      return emphasisAdded(context.getString(R.string.common_record_duplicate_message));
    } else if (MmsDatabase.Types.isNoRemoteSessionType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_mms_message_encrypted_for_non_existing_session));
    } else if (isLegacyMessage()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported));
    } else if (!getBody().isPlaintext()) {
      return emphasisAdded(context.getString(R.string.common_locked_message_description));
    }

    return super.getDisplayBody();
  }
}

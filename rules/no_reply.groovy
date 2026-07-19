// MailOrbit verification rule.
// Unattended mailboxes are pointless (and look bad) on an outreach list.
[
    name       : "no-reply",
    description: "Rejects unattended mailboxes (noreply@, mailer-daemon@, bounce@, ...)",
    apply      : { contact ->
        def local = contact.localPart.toLowerCase().replaceAll(/[._\-]/, "")
        def unattended = ["noreply", "donotreply", "dontreply", "noresponse",
                          "mailerdaemon", "bounce", "bounces", "autoreply", "autoresponder"]
        if (unattended.contains(local)) {
            return [fatal: true, reason: "Unattended mailbox (${contact.localPart}@)"]
        }
        return null
    }
]

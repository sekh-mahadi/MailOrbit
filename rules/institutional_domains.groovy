// MailOrbit verification rule.
// Government / military / academic addresses often sit behind strict spam
// filters and stricter outreach policies - downgrade, don't reject.
[
    name       : "institutional-domains",
    description: "Downgrades .gov/.mil/.edu addresses - check outreach policy before emailing",
    apply      : { contact ->
        def d = contact.domain
        if (d.endsWith(".gov") || d.endsWith(".mil") || d.endsWith(".edu")
                || d.contains(".gov.") || d.contains(".edu.")) {
            return [scoreDelta: -25, reason: "Institutional domain - verify outreach policy"]
        }
        return null
    }
]

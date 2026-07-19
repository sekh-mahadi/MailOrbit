// MailOrbit verification rule.
// Returns [name, description, apply]; apply receives the EmailContact and
// returns null (no opinion) or [scoreDelta:, reason:, fatal:].
[
    name       : "typo-domains",
    description: "Flags common misspellings of major mail providers as undeliverable",
    apply      : { contact ->
        def typos = [
            "gmial.com"   : "gmail.com",
            "gamil.com"   : "gmail.com",
            "gmali.com"   : "gmail.com",
            "gmai.com"    : "gmail.com",
            "gmail.co"    : "gmail.com",
            "hotmial.com" : "hotmail.com",
            "hotmall.com" : "hotmail.com",
            "hotmai.com"  : "hotmail.com",
            "yaho.com"    : "yahoo.com",
            "yahooo.com"  : "yahoo.com",
            "outlok.com"  : "outlook.com",
            "iclould.com" : "icloud.com"
        ]
        def suggestion = typos[contact.domain]
        if (suggestion) {
            return [fatal: true, reason: "Typo domain - did you mean ${suggestion}?"]
        }
        return null
    }
]

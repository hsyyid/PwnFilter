Proposed New Features for PwnFilter 3.2.0
=========================================

Rules file format / features
+++++++++++++++++++++++++++++

Rules.txt format
----------------

New folder structure::

    plugins/PwnFilter
             \->rulesets
             |     \-> tamewords.txt
             |     \-> badwords.txt
             |     \-> reallybadwords.txt
             |     \-> commandaliases.txt
             |     \-> etc.
             |-> signrules.txt
             |-> chatrules.txt
             |-> itemrules.txt
             \-> bookrules.txt

Each of the signrules, chatrules, etc. are rulesets for specific event
handlers.  They can import from any of the files in the rulesets directory,
and/or they can just have rules directly entered.  Eg:

chatrules.txt::

    import tamewords.txt
    import badwords.txt

    match derp
    then ...

and so on...

Named Rules
-----------
Adding a name / ID to a rule.  eg::

  rule <id> [Optional description]
    match <matchstring>
  ... etc...

Shortcuts
---------

Writing regex's can be tedious.  Shortcuts allow the use of configurable
"variables" that can are replaced in the regex.  Eg::

    match ((http)*(\w|\W|\d|_)*(www)*(\w|\W|\d|_)*[a-zA-Z0-9\.\-\*_\^\+\~\`\=\,\&*]{3,}(\W|\d|_|dot|\(dot\))+(com\b|org\b|net\b|edu\b|co\b|uk\b|de\b|cc\b|biz\b|mobi\b|xxx\b|tv\b))

could be replaced with::

    matchusing <varset> ((http)*<chr>*(www)*<chr>*<xta>{3,}<dot>+<dom>)

Internally, this would be expanded out to the regex above.

In a file called <varset>.yml, you would specify::

    chr: (\w|\W|\d|_)
    dom: (com\b|org\b|net\b|edu\b|co\b|uk\b|de\b|cc\b|biz\b|mobi\b|xxx\b|tv\b)
    dot: (\W|\d|_|dot|\(dot\))
    xta: [a-zA-Z0-9\.\-\*_\^\+\~\`\=\,\&*]

You can surround up to 3 characters with <> and they will
be replaced with whatever is defined in that varset.yml file.

Another example:

varset.yml as above, with the addition of::

    _: (\W|\d|_)
    E: [eu]
    K: [ck]

    matchusing <varset> j+<_>*<E>+<_>*r+<_>*<K>+<_>*s*

If you want to match an actual less-than (<) or greater-than (>), use a backslash (\\).

Action Groups
-------------

Sometimes, you want to have multiple rules that all do the same actions.
An Action Group allows you to predefine a set of actions which you can
then apply to a rule.  Eg::

  actiongroup swearactions
    then warn "Don't say that!"
    then fine 50 Pay $50 to the swear jar!

  .. later in the rules.txt ..

  rule L3 Match jerk
    matchusing varset j+<_>*<E>+<_>*r+<_>*<K>+<_>*s*
    then replace meanie
    then actions swearactions

Condition Groups
----------------

Just as with action groups, condition groups let you specify common conditions
you wish to apply to multiple rules.   Eg::

  conditiongroup ignoreAdmins
    ignore user Sage905
    ignore user tremor77
    ignore user DreamPhreak
    ignore user EpicATrain

  ... later in the rules.txt ...

  rule L3 Match jerk
    matchusing varset j+<_>*<E>+<_>*r+<_>*<K>+<_>*s*
    conditions ignoreAdmins
    then replace meanie
    then actions swearactions



Match Group References
----------------------
When doing an action, there is currently no way to get the actual string that
matched.  This will allow a match group to be referenced in actions.  Eg::

  match (derp)ity(dah)
  then replace $1 $2

Would match 'derpitydah' and output 'derp dah'

Respond Multiline
-----------------
Add a "then respond" action, which allows \\n to separate lines.

Respond with File
-----------------
Add then respondfile <filename.txt> which will be send to player.

Notify Action
-------------
A "then notify" action will send the notify string to any logged in player
with a given permission.  Eg:

  then notify pwnfilter.notify &player just said &rawstring

Points System
-------------

New action: then points <##>

New config: warning thresholds. drain rate

Idea:

Think of a bucket with holes in the bottom, and multiple lines on it::


  \         / -- threshold3
   \       /  -- threshold2
    \     /   -- threshold1
     - - -    -- Leak rate: points / s, or points / min

Given rules like this::

    rule S1 Fuck
     match fuck
     then points 20

    rule S2 Asshole
     match asshole
     then points 5

The following will happen:

A user will have 0 points by default.  Every time they trip the filter, it
will add the # of points (20 for 'fuck', 5 for 'asshole').  When they hit
the threshold1 level, PwnFilter will execute the commands at the threshold1
level.  When they hit thresh2, same, thresh3, same.  Every second or minute,
depending on how configured, the configured leak rate number of points will
be subtracted from the bucket.

Thus, if a player swears once in a while, they will get no warning, no
consequence.  If they have a sailor's mouth, they might get a warning at
threshold1 and 2, and a tempban at threshold3.



Event Enhancements
++++++++++++++++++

Book Support
------------
Complete support for filtering of books.

Proper Anvil Support
--------------------
This is more of a bug-fix than enhancement, but we required Bukkit to update
support for Anvils to properly filter item names.

Player Configuration
++++++++++++++++++++

Disable Filter
--------------
A player with the pwnfilter.toggleraw permission will be able to *receive* raw
messages.  This will effectively bypass any "then replace", "then rewrite"
rules in chat messages they receive. (Will not apply to signs, anvil, books, etc.)

Must take into consideration that some rules may not be 'bypassable'.


Troubleshooting
+++++++++++++++

Regex Timeout
-------------
An enhancement to the Regex which will automatically time-out if a Regex
takes more than 500ms to execute.  Upon triggering the timeout, PwnFilter
will log an error showing the failed rule as well as the text that triggered
the timeout.  This should be a big help in troubleshooting runaway regexes.



Possible enhancements for 3.2 or 3.3
++++++++++++++++++++++++++++++++++++

Web-based configuration. (Drag and drop with modals for config)

/pftest command to test a string against a rule.

Name matcher.  Basically, a special "match" rule that would detect the name
of an online player. eg: matchplayer

Name filter: apply rules to player names in onPlayerJoin event.  If player
has offensive name, then take action.

Auto-updater
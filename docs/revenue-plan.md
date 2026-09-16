# How RentManager makes money — the owner's view

Written as if I owned it: what we sell, what we charge, how the money reaches
us while hosting costs nothing, and what would kill the revenue.

## 1. What we sell, and what we deliberately do not

We sell **software to landlords**. We never touch their rent.

Rent is collected straight into each landlord's own M-Pesa Paybill or Till
using their own Daraja credentials (`CollectionMode.DIRECT`). That is a
commercial decision, not a technical one: holding other people's rent makes us
a payment aggregator, which needs Safaricom's written consent and a CBK licence
with KSh 5m core capital. We are not doing that. It also happens to be our best
trust story — *"your rent never passes through us"* — and removes any temptation
to fund the business from float.

**Renters never pay.** They are why landlords stay; charging them would kill
adoption and the data quality we depend on.

## 2. Why a landlord pays, in their words

Not "property management software". These:

- **Rent arrives earlier.** Automatic reminders and a renter app that shows the
  exact balance, with a payment button. Pulling collection forward by even 3–4
  days each month is what they feel first.
- **They stop arguing about who paid what.** An append-only ledger, receipt
  numbers, and a statement the renter can see too. Disputes end.
- **Caretakers can collect cash without stealing it.** Cash recording is
  role-limited, idempotent and timestamped, and the renter is notified.
- **KRA is handled.** Monthly Rental Income is 7.5% of gross rent, filed
  monthly. We already hold every payment, so producing the figure is nearly
  free for us and a real chore for them. This is the strongest local wedge and
  the one competitors built for other markets do not have.

## 3. Pricing

The catalogue in the database (`V50`) is:

| Plan | Units | Price |
|---|---|---|
| Starter | up to 10 | KSh 2,500/month |
| Growth | 11–30 | KSh 5,500/month |
| Portfolio | 31–75 | KSh 9,500/month |
| Enterprise | 75+ | custom, not self-service |

That is ~KSh 250 per unit per month. Against rent of KSh 8,000–15,000 a unit it
is 2–3% of collections, where a managing agent charges 5–10%. Defensible — but
only once a landlord has seen it work. So, changes I would make:

1. **A free tier that is genuinely useful: up to 3 units, forever.** Most Kenyan
   landlords start with a plot of 2–4 units. They will not buy software they
   have not lived with. The 90-day trial (`V92`) already exists for larger
   landlords; a permanent free tier is what gets word of mouth.
2. **Annual prepay at 10 months for 12.** Cash up front matters more than
   margin when there is no capital, and it locks in a year of retention.
3. **"Founding landlord" price for the first ten: 50% for life.** Testimonials
   and referrals are worth more than the missing KSh 1,250 a month, and honest
   scarcity converts.
4. **Charge on units under management, never on payment volume.** Volume-based
   pricing invites the custody question we are avoiding.

## 4. How the money actually reaches us before we have a Paybill

Today the subscription flow sends an M-Pesa STK push from the *platform's*
Daraja account. We do not have one, and a Paybill needs a registered business.
So, in order:

1. **Now (0–10 landlords):** the landlord sends the subscription to a personal
   M-Pesa number, or Pochi la Biashara (free to activate). We then activate
   their subscription from the platform admin screen — the endpoint already
   exists. Manual, auditable, costs nothing, and at this size it takes minutes
   a month.
2. **At ~KSh 20,000/month recurring:** register the business (about KSh 1,000
   for a business name), apply for a Buy Goods Till or Paybill, and turn on the
   built-in STK subscription billing. Collection becomes automatic.
3. **Never:** collecting rent into our account to take a cut. See §1.

## 5. Costs, honestly

| Item | Now | When it starts costing |
|---|---|---|
| Hosting (Render, Neon, Netlify free tiers) | KSh 0 | ~US$7/month when 0.1 CPU becomes the bottleneck |
| Sign-in (Clerk) | KSh 0 | free to 50,000 users, but needs a domain past 100 |
| Domain | KSh 0 (free subdomains) | ~KSh 1,500/year for `.co.ke` |
| Push notifications | KSh 0 | stays free |
| SMS | avoided | ~KSh 0.80/message if ever enabled |
| Google Play | KSh 0 (direct APK) | US$25 once |
| iOS | not doing it | US$99/year |

Gross margin is effectively 100% until the free tiers run out. **Ten paying
Starter landlords is KSh 25,000 a month against roughly KSh 1,000 of costs.**
That is the whole reason to stay non-custodial and free-tier: the business is
profitable at ten customers, so it never needs outside money to survive.

## 6. First 90 days

- **Weeks 1–2:** deploy (see `deploy/FREE_NO_CARD.md`), then run the app on my
  own or a relative's rental units. No pitching until I have used it for a full
  rent cycle myself.
- **Weeks 3–6:** onboard five landlords in one estate, by hand, in person.
  Enter their properties for them. Support over WhatsApp. Free, in exchange for
  honest feedback and a testimonial.
- **Weeks 7–10:** watch one number — **what share of rent gets recorded through
  the app instead of a notebook**. If it is under half, the product is not being
  adopted and no amount of selling fixes that.
- **Weeks 11–13:** convert the free landlords to Starter at the founding price,
  using their own before/after collection dates as the argument.

Target at day 90: **10 paying landlords, 150 units under management,
KSh 12,000–25,000 monthly recurring.** Small, but it is real revenue that
covers every cost with room to spare.

## 7. What would actually kill this

1. **Losing a landlord's data.** Fatal to trust and unrecoverable by apology.
   Mitigated by nightly encrypted off-site backups — and by testing a restore
   before the first real user, which is a to-do, not a done.
2. **A wrong balance shown to a renter.** The ledger is append-only and money is
   `BigDecimal` for exactly this reason. Any arithmetic or ordering bug here is
   an emergency, ahead of every feature.
3. **The 100-user sign-in cap** (Clerk development instance). Three landlords
   with thirty renters each hits it. A domain removes it. This is the first
   growth blocker and the first thing to spend money on.
4. **One instance with no redundancy.** A restart is 30–60 seconds of downtime;
   fine now, embarrassing at 50 landlords.
5. **Drifting toward custody** because someone asks us to "just collect it and
   send it on". That is the one line that turns a software business into an
   unlicensed payments business.

## 8. The numbers I would put on one screen

Weekly: landlords onboarded, units under management, share of rent recorded
in-app, renter app installs per landlord, average days-to-pay versus the month
before, trials expiring in the next 14 days, failed payment prompts.

Days-to-pay is the one that proves the product works. Everything else is
activity.

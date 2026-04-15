UPDATE blog_posts SET
    excerpt = 'ZiG is Zimbabwe''s gold-backed currency launched in April 2024. You''ve seen it on price tags and rate boards — here''s what it actually is, how the rate works, and whether it''s worth trusting.',
    content = $$<p>By now you've probably seen "ZiG" on price tags, heard it on the news, or watched the rate move on ZimRate. But a lot of people still aren't totally sure what it actually is — especially given how many currencies Zimbabwe has been through. So here's a no-nonsense breakdown.</p>

<h2>First, a bit of context</h2>
<p>Zimbabwe has changed its currency more times than most countries do in a century. The original Zimbabwe dollar collapsed in 2009 after years of hyperinflation so severe that the central bank was printing hundred-trillion-dollar notes. After that, Zimbabwe effectively just used other people's money — mostly US dollars and South African rand — for about a decade.</p>
<p>In 2019 the government tried again with the RTGS dollar, which was later rebranded as the Zimbabwe Dollar (ZWL). That didn't go well either. The ZWL shed value fast, inflation climbed again, and most people went straight back to USD for anything important.</p>
<p>ZiG — short for <strong>Zimbabwe Gold</strong> — is the latest attempt, launched on 5 April 2024. The big difference this time is that it's supposed to be backed by actual gold and foreign currency reserves held by the RBZ, rather than just being a promise on paper. Whether that holds is what everyone is watching.</p>

<h2>So what exactly is ZiG?</h2>
<p>ZiG is Zimbabwe's official domestic currency. Its ISO code is <strong>ZWG</strong> — you'll see that in bank statements and forex tables — but most people just call it ZiG. Same thing.</p>
<p>Every ZiG in circulation is meant to be backed by a basket of gold reserves and foreign currency held by the Reserve Bank. The idea is that this limits how much the RBZ can print without something tangible to show for it. Compared to the ZWL, which had no such backing, it's a meaningful structural difference.</p>

<h2>What happened to the ZWL?</h2>
<p>When ZiG launched, existing ZWL balances were converted at a rate of <strong>2,498 ZWL = 1 ZiG</strong>. So ZWL 24,980 became ZiG 10. The ZWL was then demonetised — taken out of circulation entirely — and ZiG notes and coins replaced it.</p>
<p>If that conversion rate sounds arbitrary, it was essentially pegged to where the ZWL was trading against gold at the time of launch. You didn't get to negotiate it.</p>

<h2>Can you actually use ZiG day-to-day?</h2>
<p>Yes — it's legal tender, so shops, supermarkets, and fuel stations are required to accept it. The government has also mandated ZiG for certain tax payments and government fees to force some adoption.</p>
<p>That said, USD is still king for most transactions. Rent, school fees, anything above a certain size — people default to dollars. That's not a knock on ZiG specifically, it's just the reality of a country that's been burned by local currency before. A lot of Zimbabweans operate in USD and only convert to ZiG when they have to.</p>

<h2>How does the exchange rate work?</h2>
<p>The RBZ publishes an official rate daily — this is what banks and licensed bureaus use. Then there's the parallel market rate, which is what people pay when exchanging cash informally. These two rates are often different, sometimes significantly so.</p>
<p>Reading the rate is straightforward: if the rate is 27.50 ZiG per USD, you're getting 27.50 ZiG for every dollar you exchange. If that number goes up — say to 28.50 — the ZiG has weakened. More ZiG needed to buy the same dollar. If it drops, the ZiG has strengthened.</p>
<p>ZimRate tracks both the official and parallel market rates and updates every 30 minutes, so you can see what's actually happening rather than just the official line. <a href="/">Check the current rate here.</a></p>

<h2>Is ZiG stable?</h2>
<p>More stable than the ZWL, but it hasn't been smooth sailing. There have been devaluations since launch as the RBZ adjusted the official rate under various pressures. The gold backing does give it a firmer foundation than anything Zimbabwe has tried recently, but the country still has real challenges — external debt, import dependency, strong demand for USD — that put pressure on any domestic currency.</p>
<p>The honest answer is: it's too early to know. The ZWL looked manageable for about a year before it didn't. ZiG has lasted longer and held up better, but Zimbabweans understandably want to see a longer track record before they trust it the way they trust the dollar.</p>
<p>The number most people watch isn't the official rate itself — it's the gap between the official rate and the parallel market rate. A small gap means the two are roughly in sync. A big gap means the official rate is being propped up while the market tells a different story.</p>

<h2>Quick reference</h2>
<ul>
    <li><strong>Full name:</strong> Zimbabwe Gold</li>
    <li><strong>ISO code:</strong> ZWG</li>
    <li><strong>Common name:</strong> ZiG</li>
    <li><strong>Launched:</strong> 5 April 2024</li>
    <li><strong>Issued by:</strong> Reserve Bank of Zimbabwe (RBZ)</li>
    <li><strong>Backed by:</strong> Gold reserves + foreign currency</li>
    <li><strong>Replaced:</strong> Zimbabwe Dollar (ZWL)</li>
    <li><strong>Conversion rate:</strong> 2,498 ZWL = 1 ZiG</li>
</ul>$$,
    updated_at = NOW()
WHERE slug = 'what-is-zimbabwe-gold-zig';

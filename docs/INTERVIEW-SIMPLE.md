# BidVelocity Interview Answers — Simple English Version
(Same 15 questions. Short sentences. Easy to remember, easy to speak.)

## BASIC

**Q1. What is microservices? Why did you use it?**
A monolith means one big program, one database, deploy everything together.
Microservices means many small programs. Each one does one job, has its own database, and can be updated alone.
I used it because each part has a different problem. Bidding gets heavy traffic in the last seconds. Payment is slow because of outside systems. Login is mostly reads. If they are one program, one slow part kills everything.

**Q2. What is database-per-service? Why not one database?**
Each service has its own database. Auth has users. Auction has auctions. Bidding has bids. Payment has payments.
No service can read another service's tables. If auction needs the winner, it calls the bidding API.
Why? Because a shared database joins everything together secretly. Then your "microservices" are really one big system. One change breaks everyone.

**Q3. What does the API Gateway do?**
It is the only door into the system. The browser talks only to the gateway.
The gateway checks the token, blocks fake user headers, limits too-many requests, adds a request-id for logs, and sends the call to the right service.
Without it, every service must do security again and again.

**Q4. What is Eureka for?**
Eureka is a phone book. Every service says "I am here, my port is this" when it starts.
The gateway asks Eureka "where is the bidding service?" and gets a live instance.
So I can start 3 bidding copies and the gateway shares the traffic. If one dies, Eureka removes it.

**Q5. What is a JWT? How do you check it?**
A JWT is a token with 3 parts: header, payload, signature.
Payload has user id, email, roles, and expiry time.
The server signs it with a secret. To check, the server makes the signature again and compares. If one letter changed, it fails.
Important: the token is readable but not changeable. Signed, not hidden.

**Q6. Authentication vs Authorization?**
Authentication = who are you? I check email + password with BCrypt and give a JWT.
Authorization = what can you do? Roles are inside the token: USER, SELLER, ADMIN.
Only SELLER creates auctions. Only ADMIN refunds. And also: you can only pay YOUR invoice, only cancel YOUR auction.

## MEDIUM

**Q7. Why BCrypt and not SHA-256 for passwords?**
SHA is fast. Fast is bad for passwords — a hacker's GPU tries billions per second.
BCrypt is made to be slow, about 100 milliseconds per check. So cracking takes years, not hours.
BCrypt also adds salt automatically, so two users with the same password get different hashes.

**Q8. Why refresh tokens? What is rotation and reuse detection?**
The access token lives only 2 hours. If a thief steals it, damage is small.
But asking the user to login every 2 hours is bad. So we also give a refresh token that lives 14 days and gets new access tokens.
Rotation: every time you use the refresh token, it dies and gives you a new one.
Reuse detection: if an OLD refresh token comes back, it means a thief has a copy. So we kill the whole family — user must login again.
Small detail: the kill runs in its own transaction, because the error I throw right after would otherwise roll back the kill.

**Q9. Two people bid the same amount at the same time. How is only one accepted?**
Three guards inside one database transaction:
1. Advisory lock — every bid for this auction waits in line, across all server copies.
2. Row lock (SELECT FOR UPDATE) — the auction's price row is locked while I work.
3. The final UPDATE has the old price inside its WHERE clause. So if someone moved the price first, my WHERE matches 0 rows and I return 409 "price moved, new minimum is X".
Only one UPDATE can match. The rest fail cleanly.
And I tested this on real PostgreSQL with 24 parallel bidders — exactly one won.

**Q10. What is an Idempotency-Key?**
Every bid request carries a unique key from the client (a UUID).
The database has a UNIQUE rule on it.
So if the user clicks twice, or the network retries, the second request finds the key already used and returns the first result. No double bid, ever.

**Q11. What is the transactional outbox?**
Problem: the database commits, then we publish an event. If the server dies in between, the event is lost. Or the event is published but the commit fails — now we have a lie.
Solution: write the event as a ROW in the same database, in the same transaction. When the auction becomes SOLD, WINNER_DECLARED is inserted at the same moment. A reader (payment service) picks it up later.
Delivery is "at least once", so the consumer must ignore duplicates — it does that with a unique key.

**Q12. When do services call each other directly vs use events?**
Direct call (sync): when I need the answer right now. Example: at closing time, auction asks bidding "who won?" — it must know now.
Event (async): when I don't care when the other side finishes. Example: after SOLD, payment can take 5 seconds or 5 minutes — bidding and auction must not wait.
Bad design is a long chain: bid → auction → payment, all synchronous. One slow link freezes everything.

## SENIOR FOLLOW-UPS

**Q13. What if the payment service is down when an auction sells?**
Nothing bad happens to the sale. SOLD and the event are already committed together.
When payment comes back, it reads the missed events and creates the invoice. Duplicates are ignored because of the unique key.
The user sees the invoice a bit late. The sale is never lost.

**Q14. Why HS256? What would you change for production?**
HS256 uses one shared secret. Every service that can check tokens can also make them. For my small trusted system that is OK.
For production I would use RS256: only the auth service holds the private key; others get only the public key.
Also: refresh token in an HttpOnly cookie instead of localStorage, so XSS cannot steal it.

**Q15. Hardest bug you faced?**
First-bid race. When the very first bid arrives, the price-tracking row does not exist yet. 24 threads tried to create it at the same time.
My tests on H2 passed, but real PostgreSQL showed the bug: H2 does not re-check the WHERE clause after waiting for a lock, PostgreSQL does.
Fix: one creator per auction (a small in-memory gate), plus the advisory lock, plus the compare-and-swap update. Then I moved the concurrency tests to real PostgreSQL so the test matches production.
Lesson I tell my team: test on the database you run.

---
## One-line answers for quick questions
- **Optimistic lock**: save a version number, fail the write if it changed. Used on auctions.
- **Pessimistic lock**: lock the row so others wait. Used on the bid runtime row.
- **Correlation id**: one id from the gateway through all service logs, so you can trace one request.
- **Flyway**: SQL files with version numbers, applied in order, checked with checksums.
- **STOMP/WebSocket**: the server pushes "new bid" to every open browser instantly.
- **Stateless service**: no session on the server; the token carries everything. So I can run 3 copies.

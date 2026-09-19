export type Role = 'USER' | 'SELLER' | 'ADMIN'
export interface User { id: number; email: string; firstName: string; lastName: string; status: string; roles: Role[]; provider: string }
export interface AuthResponse { accessToken: string; refreshToken: string; expiresInSeconds: number; user: User }

export type AuctionStatus = 'DRAFT' | 'SCHEDULED' | 'LIVE' | 'ENDING' | 'ENDED' | 'CANCELLED' | 'SOLD' | 'UNSOLD'
export interface Auction {
  id: number; sellerId: number; title: string; description: string; category: string; emoji: string
  startingPrice: number; currentPrice: number; minIncrement: number; reservePrice: number | null
  startTime: string; endTime: string; status: AuctionStatus
  antiSnipingEnabled: boolean; extensionWindowSecs: number; maxExtensions: number; extensionCount: number
  bidCount: number; highestBidId: number | null; winnerId: number | null; winningAmount: number | null
  closeReason: string | null; version: number; createdAt: string; updatedAt: string; minNextBid: number
}

export interface Bid { id: number; auctionId: number; bidderId: number; bidderName: string; amount: number; status: string; rejectReason: string | null; at: string }
export interface Payment { id: number; auctionId: number; auctionTitle: string; sellerId: number; winnerId: number; amount: number; currency: string; status: 'PENDING'|'PROCESSING'|'SUCCESS'|'FAILED'|'EXPIRED'|'REFUNDED'; provider: string; providerRef: string | null; failureReason: string | null; attempts: number; createdAt: string; updatedAt: string }

export interface Paged<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number }

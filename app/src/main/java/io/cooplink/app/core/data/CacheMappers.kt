package io.cooplink.app.core.data

import io.cooplink.app.core.domain.LoanStatus
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.domain.Transaction
import io.cooplink.app.feature.member.disputes.Dispute
import io.cooplink.app.feature.member.notifications.NotificationItem
import io.cooplink.app.feature.member.savingsgoals.SavingsGoal
import io.cooplink.app.feature.member.standingorders.StandingOrder

fun MemberDetails.toEntity() = MemberEntity(
    id            = id,
    userId        = userId,
    cooperativeId = cooperativeId,
    status        = status,
    fullName      = fullName,
    phone         = phone,
    avatarUrl     = avatarUrl,
    email         = email,
    totalSavings  = totalSavings,
    createdAt     = createdAt,
    memberNumber  = memberNumber,
    loginId       = loginId,
)

fun MemberEntity.toDomain() = MemberDetails(
    id            = id,
    userId        = userId,
    cooperativeId = cooperativeId,
    status        = status,
    fullName      = fullName,
    phone         = phone,
    avatarUrl     = avatarUrl,
    email         = email,
    totalSavings  = totalSavings,
    createdAt     = createdAt,
    memberNumber  = memberNumber,
    loginId       = loginId,
)

fun Transaction.toEntity(fallbackMemberId: String) = TransactionEntity(
    id          = id,
    memberId    = memberId ?: fallbackMemberId,
    type        = type,
    amount      = amount,
    description = description,
    status      = "posted",
    createdAt   = createdAt,
)

fun TransactionEntity.toDomain() = Transaction(
    id        = id,
    memberId  = memberId,
    type      = type,
    amount    = amount,
    description = description,
    createdAt = createdAt,
)

fun LoanEntity.isActive(): Boolean = status in LoanStatus.ACTIVE_STATUSES

fun NotificationItem.toEntity(userId: String) = NotificationEntity(
    id = id, userId = userId, cooperativeId = null,
    title = title, message = message, type = type, isRead = is_read, createdAt = created_at,
)

fun NotificationEntity.toDomain() = NotificationItem(
    id = id, title = title, message = message, type = type, is_read = isRead, created_at = createdAt,
)

fun StandingOrder.toEntity() = StandingOrderEntity(
    id = id, memberId = member_id, cooperativeId = cooperative_id,
    amount = amount, frequency = frequency, dayOfMonth = day_of_month,
    purpose = purpose, startDate = start_date, endDate = end_date,
    active = active, createdAt = created_at,
)

fun StandingOrderEntity.toDomain() = StandingOrder(
    id = id, member_id = memberId, cooperative_id = cooperativeId,
    amount = amount, frequency = frequency, day_of_month = dayOfMonth,
    purpose = purpose, start_date = startDate, end_date = endDate,
    active = active, created_at = createdAt,
)

fun Dispute.toEntity() = DisputeEntity(
    id = id, memberId = member_id, cooperativeId = cooperative_id, transactionId = transaction_id,
    description = description, status = status, category = category,
    resolutionNotes = resolution_notes, resolvedAt = resolved_at, resolvedBy = resolved_by, createdAt = created_at,
)

fun DisputeEntity.toDomain() = Dispute(
    id = id, member_id = memberId, cooperative_id = cooperativeId, transaction_id = transactionId,
    description = description, status = status, category = category,
    resolution_notes = resolutionNotes, resolved_at = resolvedAt, resolved_by = resolvedBy, created_at = createdAt,
)

fun SavingsGoal.toEntity() = SavingsGoalEntity(
    id = id, memberId = member_id, cooperativeId = cooperative_id, goalName = goal_name,
    targetAmount = target_amount, currentAmount = current_amount, targetDate = target_date,
    status = status, createdAt = created_at,
)

fun SavingsGoalEntity.toDomain() = SavingsGoal(
    id = id, member_id = memberId, cooperative_id = cooperativeId, goal_name = goalName,
    target_amount = targetAmount, current_amount = currentAmount, target_date = targetDate,
    status = status, created_at = createdAt,
)

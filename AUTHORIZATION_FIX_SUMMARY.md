# Process Search Authorization Fix - Implementation Summary

## Overview
Fixed security vulnerability in `/egov-workflow-v2/egov-wf/process/_search` endpoint to enforce role-based access control and prevent unauthorized access to process instances.

## Security Issue
**Vulnerability**: Users could bypass authorization checks by providing explicit search criteria (businessIds, states, statuses) instead of null criteria, allowing access to all processes regardless of authorization level.

**Risk Level**: High - Complete access bypass to all process data

## Solution Implemented

### 1. Authorization Validation Method
Added `validateUserAccessToProcesses()` method that performs role-based authorization checks on process instances:

```java
public void validateUserAccessToProcesses(RequestInfo requestInfo, List<ProcessInstance> processInstances)
```

**Behavior:**
- Validates user information is present
- Checks user's roles and associations with process instances
- Throws `UNAUTHORIZED_ACCESS` exception (HTTP 403) if no match found

### 2. Role-Based Access Rules

#### **EMPLOYEE Role**
- **Check**: Current user's UUID must be in `processInstance.assignees[].uuid`
- **Logic**: Employee can only view processes they are assigned to
- **Implementation**: `isUserInAssignees()` method

#### **ARCHITECT/CREATOR Role** (e.g., BPA_ARCHITECT)
- **Check**: Current user's UUID must match `processInstance.assigner.uuid`
- **Alternative**: Check `auditDetails.createdBy`
- **Logic**: Architect/Creator can only view processes they initiated or assigned
- **Implementation**: `isUserAssigner()` method

#### **CITIZEN Role**
- **Recommended Approach 1** (Currently Implemented):
  - Check if user UUID matches `auditDetails.createdBy` (application creator)
  - This assumes the application creator is the citizen who submitted it
  - **Pros**: No additional queries, minimal performance impact
  - **Cons**: May not cover all scenarios if application ownership can change

- **Recommended Approach 2** (Enhanced - Requires Additional Implementation):
  - First search application details by `businessIds`
  - Match application's `applicant.uuid` with current user UUID
  - **Pros**: More accurate ownership verification
  - **Cons**: Additional database query, higher latency
  
- **Recommended Approach 3** (Best Practice):
  - Extend ProcessInstance with `applicant` field populated during enrichment
  - Check `processInstance.applicant.uuid` against current user
  - **Pros**: Centralized data, consistent with employee/architect checks
  - **Cons**: Requires schema/model changes

### 3. Updated Search Flow

```
search(requestInfo, criteria)
    ├─ Validate tenantId present
    ├─ Validate requestInfo.userInfo present
    ├─ If criteria is null:
    │   └─ Use getUserBasedProcessInstances (role-based filtering)
    └─ If criteria is provided:
        ├─ Fetch process instances from repository
        └─ validateUserAccessToProcesses()
            ├─ Extract user UUID and roles
            ├─ For each process instance:
            │   ├─ If EMPLOYEE: Check assignees
            │   ├─ If ARCHITECT: Check assigner/creator
            │   └─ If CITIZEN: Check creator/applicant
            └─ Throw 403 if no match
```

## Files Modified

### [WorkflowService.java](core-services/egov-workflow-v2/src/main/java/org/egov/wf/service/WorkflowService.java)

**Added Methods:**
1. `validateUserAccessToProcesses()` - Main validation orchestrator
2. `hasRole()` - Role existence checker
3. `isArchitectRole()` - Detects architect/creator roles
4. `isUserInAssignees()` - Employee access validation
5. `isUserAssigner()` - Citizen/Architect owner validation

**Modified Methods:**
- `search()` - Now calls `validateUserAccessToProcesses()` for explicit search criteria

**Added Imports:**
- `org.egov.common.contract.request.Role`
- `org.egov.common.contract.request.User`

## Authorization Response Codes

| Scenario | HTTP Code | Error Code | Message |
|----------|-----------|------------|---------|
| No user info provided | 403 | UNAUTHORIZED_ACCESS | User information is required to search processes. Access Denied (403) |
| User not in assignees (Employee) | 403 | UNAUTHORIZED_ACCESS | User with role does not have access to this process. Access Denied (403) |
| User not in assigner (Architect) | 403 | UNAUTHORIZED_ACCESS | User with role does not have access to this process. Access Denied (403) |
| User not creator (Citizen) | 403 | UNAUTHORIZED_ACCESS | User with role does not have access to this process. Access Denied (403) |

## Implementation Details

### Role Detection
```java
// EMPLOYEE check
boolean isEmployee = hasRole(roles, "EMPLOYEE");

// ARCHITECT/CREATOR check (matches: BPA_ARCHITECT, "*_ARCHITECT", "*_CREATOR", "*_INITIATOR")
boolean isArchitect = roles.stream()
    .anyMatch(role -> role.getCode() != null && 
        (role.getCode().contains("ARCHITECT") || 
         role.getCode().contains("CREATOR") ||
         role.getCode().contains("INITIATOR")));
```

### Citizen Applicant Verification
Currently implemented via two methods:
```java
// Method 1: Check auditDetails.createdBy
processInstance.getAuditDetails().getCreatedBy() == userUuid

// Method 2: Check assigners (if populated from application data)
processInstance.getAssigners().stream()
    .anyMatch(user -> userUuid.equalsIgnoreCase(user.getUuid()))
```

## Recommendations for Citizen Role Enhancement

### Option A: Query Application Service (Recommended for Accuracy)
**Pros:**
- Most accurate applicant verification
- Handles application ownership changes
- Decoupled from workflow service

**Cons:**
- Additional service call required
- Higher latency
- External dependency

**Implementation:**
```java
// In WorkflowService or separate AuthorizationService
private boolean validateCitizenAccess(ProcessInstance process, String userUuid, String tenantId) {
    // Call BPA/Application service to get applicant details
    ApplicationDetails appDetails = applicationService.getApplicationByBusinessId(
        process.getBusinessId(), tenantId);
    
    return userUuid.equals(appDetails.getApplicant().getUuid());
}
```

### Option B: Enrich ProcessInstance with Applicant Data (Recommended for Performance)
**Pros:**
- Single query, consistent data
- Best performance
- Unified access pattern

**Cons:**
- Requires model/schema changes
- Additional enrichment logic

**Implementation:**
```java
// In EnrichmentService.enrichUsersFromSearch()
// Add applicant details to processInstance
List<ApplicationDetails> apps = getApplicationsByBusinessIds(businessIds);
appsMap = apps.groupBy(a -> a.getBusinessId());

for(ProcessInstance process : instances) {
    ApplicationDetails app = appsMap.get(process.getBusinessId());
    if(app != null) {
        process.setApplicant(app.getApplicant());
    }
}
```

### Option C: Current Implementation (Fast Track)
**Pros:**
- No external calls required
- Immediate implementation
- Uses existing data

**Cons:**
- Assumes created_by = applicant
- May not cover edge cases

**Use when:** Application creator is always the applicant (typical for BPA/citizen services)

## Testing Scenarios

### Test Case 1: Employee Access
```
User Role: EMPLOYEE
User UUID: emp-001
Process Assignees: [emp-001, emp-002]
Expected: ✓ ALLOWED (user in assignees)
```

### Test Case 2: Architect Access
```
User Role: BPA_ARCHITECT
User UUID: arch-001
Process Assigner: arch-001
Expected: ✓ ALLOWED (user is assigner)
```

### Test Case 3: Citizen Access
```
User Role: CITIZEN
User UUID: cit-001
Process Created By: cit-001
Expected: ✓ ALLOWED (user is creator)
```

### Test Case 4: Unauthorized Access
```
User Role: EMPLOYEE
User UUID: emp-999
Process Assignees: [emp-001, emp-002]
Expected: ✗ DENIED (403) - user not in assignees
```

## Performance Considerations

- **In-memory validation**: Role and UUID checks done in memory (no DB queries)
- **Single database query**: Process instances fetched once, validation on retrieved data
- **Scalability**: No N+1 queries, validation time O(n) per process instance

## Migration Notes

If implementing Option B (enriched applicant data):
1. Add `applicant` field to ProcessInstance model
2. Update EnrichmentService to populate applicant data
3. Update ProcessInstance search queries to include applicant info
4. Backward compatibility: Make field optional initially

## Security Best Practices Applied

✓ Principle of Least Privilege: Users only see authorized data
✓ Defense in Depth: Multiple validation layers (search criteria + instance validation)
✓ Fail Secure: Default deny access, explicit allow on match
✓ Logging Ready: Custom exception codes enable audit trail
✓ User Context: All checks based on RequestInfo user information

## Next Steps

1. **For Citizen Role Enhancement:**
   - Decide between Option A, B, or C above
   - Implement chosen approach
   - Add comprehensive test cases

2. **Testing:**
   - Unit test role detection logic
   - Integration test with actual process instances
   - Security test with unauthorized users

3. **Monitoring:**
   - Log UNAUTHORIZED_ACCESS exceptions
   - Monitor 403 response rates
   - Alert on unusual access patterns

## References

- **Vulnerability Report**: /egov-workflow-v2/egov-wf/process/_search - Access to processes for all requests
- **Error Response Code**: 403 Forbidden (HTTP)
- **Exception Code**: UNAUTHORIZED_ACCESS (Custom)

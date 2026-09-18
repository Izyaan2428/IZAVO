IZAVO BML TORTURE TEST V2
Fully synthetic. No real user data.

Use only these V2 files for classifier evaluation; retire the earlier V1 fixtures.

Headerless 11-column BML-like structure:
1 Posted date
2 Value date
3 Transaction type
4 Primary reference
5 Secondary/core reference
6 Transaction detail / counterparty
7 Description
8 Additional detail / timestamp
9 Debit
10 Credit
11 Running balance

V2 correction:
Ambiguous transfers/Favara transactions put the readable person/business identity in columns 6 and 7.
The timestamp is in column 8. This is designed to exercise IZAVO's real review identity extraction without presenting a timestamp as the merchant/person.

Recommended order:
Level 1 (100) -> manually inspect/review.
Level 2 (500) -> grouping/learning/duplicates.
Level 3 (1200) -> scale/performance.
Then import LEVEL3_OVERLAP_401_900 after Level 3 to test overlap protection.

GROUND_TRUTH files contain the intended correct semantic outcome and review choice for every synthetic row.

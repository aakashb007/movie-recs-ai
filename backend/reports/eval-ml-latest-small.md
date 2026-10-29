# Offline evaluation: ml-latest-small

Chosen on validation: ALS `{'factors': 32, 'reg': 1.0, 'alpha': 5.0}`, hybrid `{'shrink': 10.0, 'knn_share': 0.5, 'pop_weight': 0.25, 'cold_pop': 0.5}`.

## Warm users (latest 20% of each history held out)

Users evaluated: 590

| model | ndcg@10 | recall@10 | coverage | diversity | novelty |
|---|---|---|---|---|---|
| popularity | 0.0752 | 0.0743 | 0.0263 | 0.8185 | 8.4797 |
| item_knn | 0.1025 | 0.1097 | 0.1400 | 0.7751 | 9.3572 |
| als | 0.0935 | 0.1068 | 0.2159 | 0.7804 | 9.5855 |
| content | 0.0179 | 0.0220 | 0.4647 | 0.1938 | 12.4897 |
| hybrid | 0.1099 | 0.1201 | 0.0940 | 0.7936 | 8.9414 |
| hybrid+mmr | 0.1096 | 0.1199 | 0.0932 | 0.8234 | 8.9304 |

## Cold-start users (unseen, 5 ratings given)

Users evaluated: 122

| model | ndcg@10 | recall@10 | coverage | diversity | novelty |
|---|---|---|---|---|---|
| popularity | 0.3098 | 0.2873 | 0.0041 | 0.8103 | 8.3095 |
| item_knn | 0.2476 | 0.2364 | 0.1290 | 0.7386 | 9.9877 |
| als | 0.2638 | 0.2593 | 0.1090 | 0.7539 | 9.5892 |
| content | 0.0708 | 0.0607 | 0.2425 | 0.2681 | 12.0517 |
| hybrid | 0.3482 | 0.3287 | 0.0375 | 0.7815 | 8.5705 |
| hybrid+mmr | 0.3563 | 0.3380 | 0.0345 | 0.8129 | 8.5340 |

Source: CI run 40947151557 (commit f321a81). Regenerated on every push by the Backend workflow.

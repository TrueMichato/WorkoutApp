# WorkoutApp

A local-first Android workout app focused on two jobs:

1. tracking what you train
2. deciding what to train next without losing variety or balance

## Current phase-8 status

Phase 8 adds **ML Enhancement — On-device learning from user behavior**:

### Phase 8 — ML Enhancement
- **MLFeedbackEvent** entity — records user acceptance/rejection/completion/skip of exercise suggestions with full context (day, hour, time slot, days since exercise/category, difficulty, etc.)
- **MLPreferenceScore** entity — cached preference scores for exercises and categories with confidence levels
- **MLFeatureVector** — normalized feature vector for future TensorFlow Lite integration
- **MLFeedbackDao** — Room DAO for feedback events and preference scores with aggregation queries
- **MLFeedbackRepository** — records suggestions, rejections, swaps, completions; calculates Wilson score lower bounds for preference scores; feature extraction
- **WorkoutRecommender** — ML-based adjustment scoring:
  - Exercise preference weight (40%)
  - Category preference weight (20%)
  - UCB exploration bonus for less-sampled exercises (10%)
  - Novelty bonus for new/rarely-done exercises (10%)
  - Contextual pattern weight (20%, future)
- **WorkoutPlanner integration** — ML scores loaded and applied as 20% boost to base rule scores
- **Feedback recording** — completion feedback automatically updates ML events for learning
- **TensorFlow Lite** dependency added for future model integration
- **Unit tests**: 12 MLRecommender tests (acceptance rates, confidence, UCB exploration, score combinations)

### Previous phases
- **Phase 7**: Dashboard & analytics with balance visualization, progress charts, storage management
- **Phase 6**: PT section with must-do scheduling, pain tracking, session history
- **Phase 5**: Per-set logging, richer history with status filters
- **Phase 4**: Rule-based workout generator with goal-adaptive weighting
- **Phase 3**: Equipment & location management
- **Phase 2**: Exercise library CRUD with media
- **Phase 1**: Architecture, Room DB, models, navigation shell

## Quick validation

```zsh
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "com.example.workoutapp.domain.*"
```

## ML Architecture

The ML layer uses a **multi-signal approach**:

1. **Statistical Layer** — Wilson score confidence intervals for exercise/category acceptance rates
2. **Exploration Layer** — Upper Confidence Bound (UCB1) for balancing exploitation vs exploration
3. **Contextual Layer** — Time-of-day and day-of-week patterns (ready for expansion)
4. **Feature-based Layer** — Normalized feature vectors ready for TensorFlow Lite model

The system learns from every workout:
- Accepted suggestions → positive signal
- Completed exercises → strong positive signal
- Rejected suggestions → negative signal
- Skipped exercises → negative signal
- Swapped exercises → negative signal for original

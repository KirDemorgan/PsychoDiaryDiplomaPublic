package alex.drankov.diary.analytics.entity;

import alex.drankov.diary.ratings.entity.StateRating;

public enum Metric {
    ANXIETY, MOOD, ENERGY;

    public Integer value(StateRating rating) {
        return switch (this) {
            case ANXIETY -> rating.getAnxiety();
            case MOOD -> rating.getMood();
            case ENERGY -> rating.getEnergy();
        };
    }
}

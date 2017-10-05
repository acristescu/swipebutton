package io.zenandroid.swipebutton;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.widget.FrameLayout;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {
    public static final int SLIDE_OUT_DURATION = 333;

    @BindView(R.id.swipe_button) SwipeButton swipeButton;
    @BindView(R.id.card) CardView cardView;
    @BindView(R.id.card_container) FrameLayout cardContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        init(savedInstanceState);
    }

    private void init(Bundle savedInstanceState) {
        swipeButton.getProgressObservable().subscribe(progress -> {
            final int translation = (int) (progress * cardContainer.getWidth() / 50f);
            cardContainer.setPadding(translation, 0, -translation, 0);
        });
        swipeButton.getCompleteObservable().subscribe(aVoid -> {
            final int activityHeight = findViewById(android.R.id.content).getHeight();
            swipeButton.animate().yBy(activityHeight - swipeButton.getY()).setDuration(SLIDE_OUT_DURATION);
            cardContainer.animate().yBy(activityHeight - cardContainer.getY()).setDuration(SLIDE_OUT_DURATION);
        });
    }
}

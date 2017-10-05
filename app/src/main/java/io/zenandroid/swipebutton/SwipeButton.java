package io.zenandroid.swipebutton;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.animation.FlingAnimation;
import android.support.animation.FloatValueHolder;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 * Created by alex on 03/10/2017.
 */

public class SwipeButton extends FrameLayout {

    public static final double THRESHOLD_FRACTION = .85;
    public static final int ANIMATE_TO_START_DURATION = 300;
    public static final int ANIMATE_TO_END_DURATION = 200;
    public static final int ANIMATE_SHAKE_DURATION = 2000;
    public static final float FLING_FRICTION = .85f;

    private Unbinder unbinder;
    private GestureDetector gestureDetector;
    private ValueAnimator animator;
    private FlingAnimation flingAnimation;
    private boolean triggered = false;
    private PublishSubject<Float> progressSubject = PublishSubject.create();
    private PublishSubject<Void> completeSubject = PublishSubject.create();

    @BindView(R.id.overlay) View overlayView;
    @BindView(R.id.swipe_text) TextView textView;
    @BindView(R.id.swipe_button_background) View buttonBackground;
    @BindView(R.id.swipe_check) AppCompatImageView checkmark;

    public SwipeButton(@NonNull Context context) {
        super(context);
        init();
    }

    public SwipeButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SwipeButton(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SwipeButton(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        View view = inflate(getContext(), R.layout.swipe_button, this);
        unbinder = ButterKnife.bind(view);
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent motionEvent) {
                animateShakeButton();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
                cancelAnimations();
                setDragProgress(motionEvent1.getX());
                return true;
            }

            @Override
            public boolean onFling(MotionEvent downEvent, MotionEvent moveEvent, float velocityX, float velocityY) {
                if(velocityX < 0) {
                    return false;
                }
                cancelAnimations();
                flingAnimation = new FlingAnimation(new FloatValueHolder(moveEvent.getX()));
                flingAnimation.setStartVelocity(velocityX)
                        .setMaxValue(getWidth())
                        .setFriction(FLING_FRICTION)
                        .addUpdateListener((dynamicAnimation, val, velocity) -> setDragProgress(val))
                        .addEndListener((dynamicAnimation, canceled, val, velocity) -> onDragFinished(val))
                        .start();

                return true;
            }
        });
        gestureDetector.setIsLongpressEnabled(false);
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        unbinder.unbind();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if(triggered) {
            return true;
        }
        if(gestureDetector.onTouchEvent(event)) {
            return true;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                onDragFinished(event.getX());
                break;
        }

        return true;
    }

    private void setDragProgress(float x) {
        progressSubject.onNext(x / getWidth());
        final int translation = calculateTranslation(x);
        setPadding(translation, 0, - translation, 0);
        if(!triggered) {
            overlayView.setAlpha(x / getWidth());
            overlayView.getLayoutParams().width = (int) Math.min(x - overlayView.getX() - translation, buttonBackground.getWidth());
            overlayView.requestLayout();

            textView.setAlpha(1 - overlayView.getAlpha());
        } else {
            overlayView.setAlpha(1);
            overlayView.getLayoutParams().width = buttonBackground.getWidth();
            overlayView.requestLayout();

            textView.setAlpha(0);
        }
    }

    private int calculateTranslation(float x) {
        return (int) x / 25;
    }

    private void cancelAnimations() {
        if(animator != null) {
            animator.cancel();
        }
        if(flingAnimation != null) {
            flingAnimation.cancel();
        }
    }

    private void onDragFinished(float finalX) {
        if(finalX > THRESHOLD_FRACTION * getWidth()) {
            animateToEnd(finalX);
        } else {
            animateToStart();
        }
    }

    private void animateToStart() {
        cancelAnimations();
        animator = ValueAnimator.ofFloat(overlayView.getWidth(), 0);
        animator.addUpdateListener(valueAnimator -> setDragProgress((Float)valueAnimator.getAnimatedValue()));
        animator.setDuration(ANIMATE_TO_START_DURATION);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if(triggered) {
                    completeSubject.onNext(null);
                }
            }
        });
        animator.start();
    }

    private void animateToEnd(float currentValue) {
        cancelAnimations();
        float rightEdge = buttonBackground.getWidth() + buttonBackground.getX();
        rightEdge += calculateTranslation(rightEdge);
        animator = ValueAnimator.ofFloat(currentValue, rightEdge);
        animator.addUpdateListener(valueAnimator -> setDragProgress((Float)valueAnimator.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                triggered = true;
                checkmark.animate().alpha(1).setDuration(ANIMATE_TO_START_DURATION);
                animateToStart();
            }
        });
        animator.setDuration(ANIMATE_TO_END_DURATION);
        animator.start();
    }

    private void animateShakeButton() {
        cancelAnimations();
        float rightEdge = buttonBackground.getWidth() + buttonBackground.getX();
        rightEdge += calculateTranslation(rightEdge);
        animator = ValueAnimator.ofFloat(0, rightEdge, 0, rightEdge / 2, 0, rightEdge / 4, 0);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            final int translation = calculateTranslation((Float)valueAnimator.getAnimatedValue());
            setPadding(translation, 0, - translation, 0);
        });
        animator.setDuration(ANIMATE_SHAKE_DURATION);
        animator.start();
    }

    public Observable<Float> getProgressObservable() {
        return progressSubject.asObservable();
    }

    public Observable<Void> getCompleteObservable() {
        return completeSubject.asObservable();
    }
}

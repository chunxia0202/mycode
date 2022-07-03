import java.util.*;

class Solution {
    public int[] maxSlidingWindow(int[] nums, int k) {
        Deque<Integer> deque = new LinkedList<>();
        int left = 0;
        int[] res = new int[nums.length - k + 1];
        for(int i = 0; i < nums.length; i++) {
            while(!deque.isEmpty() && nums[deque.peekLast()] <= nums[i]) {
                deque.pollLast();
            }
            deque.addFirst(i);
            if(deque.peekFirst() == i-k) deque.pollFirst();
            if(i - k + 1 >= 0) res[i-k+1] = nums[deque.peekFirst()];
        }
        return res;
    }

    public static void main(String[] args) {
        Solution solution = new Solution();
        solution.maxSlidingWindow(new int[]{7,2,4},2);
    }

}
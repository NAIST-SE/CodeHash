/* Compute the n-th term and the sum of an arithmetic progression using formulas. */
#include <stdio.h>

static int nth_term(int a, int d, int n) {
	return a + (n - 1) * d;
}

static int series_sum(int a, int d, int n) {
	return n * (2 * a + (n - 1) * d) / 2;
}

int main(void) {
	int a = 3, d = 4, n = 10;
	printf("a[%d] = %d\n", n, nth_term(a, d, n));
	printf("sum = %d\n", series_sum(a, d, n));
	return 0;
}

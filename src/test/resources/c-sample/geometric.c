/* Print a geometric progression and its sum. */
#include <stdio.h>

int main(void) {
	long first = 3;
	long ratio = 2;
	int count = 10;
	long term = first;
	long sum = 0;
	int i;

	for (i = 0; i < count; i++) {
		printf("g[%d] = %ld\n", i, term);
		sum += term;
		term *= ratio;
	}
	printf("sum = %ld\n", sum);
	return 0;
}

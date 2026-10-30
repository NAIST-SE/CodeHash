/* Print an arithmetic progression and its sum. */
#include <stdio.h>

int main(void) {
	int first = 3;
	int diff = 4;
	int count = 10;
	int term = first;
	int sum = 0;
	int i;

	for (i = 0; i < count; i++) {
		printf("a[%d] = %d\n", i, term);
		sum += term;
		term += diff;
	}
	printf("sum = %d\n", sum);
	return 0;
}

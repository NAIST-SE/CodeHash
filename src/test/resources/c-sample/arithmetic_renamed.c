// A copy of arithmetic.c whose identifiers are renamed.
#include <stdio.h>

int main(void) {
	int start = 3;
	int step = 4;
	int length = 10;
	int value = start;
	int total = 0;
	int k;

	for (k = 0; k < length; k++) {
		printf("a[%d] = %d\n", k, value);
		total += value;
		value += step;
	}
	printf("sum = %d\n", total);
	return 0;
}

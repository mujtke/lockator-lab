/**
 * 不加锁情况下的测试
 */


#include<pthread.h>
#include<stdio.h>

int b = 0;
int c = 1;

pthread_t t2;

void *thread2(void *arg) {
	b = 2;
	printf("%d, b = %d\n", (c++), b);
}

void main() {
	b = 1;

	printf("main:\n");
	int a = 0;
	while(a < 3) {
		if (a >= 0) {
			pthread_create(&t2, NULL, thread2, NULL);
		}
		a++;
	}

	return;
}
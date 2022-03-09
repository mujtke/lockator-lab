#include<pthread.h>

int b = 0;

pthread_t t1;

void *thread1(void *arg) {
	int a = 0;
	while(a < 3) {
		if (a > 1) {
			b = 2;
		}
		a++;
	}
}

void main() {
	b = 1;

	pthread_create(&t1, NULL, thread1, NULL);

	b = 3;

	return;
}
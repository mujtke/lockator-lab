#include<pthread.h>

int b = 0;

pthread_t t1;
pthread_t t2;

void *thread1(void *arg) {
	int a = 0;
	while(a < 3) {
		if (a > 0) {
			pthread_create(&t2, NULL, thread2, NULL);
		}
		a++;
	}
}

void *thread2(void *arg) {
	b = 2;
}

void main() {
	b = 1;

	pthread_create(&t1, NULL, thread1, NULL);

	return;
}
#include<pthread.h>

int b = 0;

pthread_t t2;

void *thread2(void *arg) {
	b = 2;
}

void main() {
	b = 1;

	int a = 0;
	while(a < 3) {
		if (a >= 0) {
			pthread_create(&t2, NULL, thread2, NULL);
		}
		a++;
	}

	return;
}